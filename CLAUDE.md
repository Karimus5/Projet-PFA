# CLAUDE.md — Plateforme intelligente de gestion des réclamations clients

PFA 2026-2027 (EMSI Casablanca, sujet 20). Référence fonctionnelle : `docs/cahier-des-charges.pdf`
(version 2 ; BF1–BF26, besoins non fonctionnels, architecture, UML). **Le relire avant toute nouvelle étape.**
Ne pas faire de choix d'architecture hors cahier des charges sans demander à l'utilisateur.

## Résumé
Plateforme web où les clients soumettent des réclamations, classées automatiquement par un modèle ML
(catégorie + service + score de confiance), puis validées/corrigées par des agents. Repli en classement
manuel si l'IA est indisponible ou peu confiante. Rôles : client, agent, administrateur.

## Architecture (microservices, une base par service)
| Service                | Techno                     | Port  | Base           | Statut   |
|------------------------|----------------------------|-------|----------------|----------|
| config-server          | Spring Cloud Config (native) | 8888 | —            | fait     |
| discovery-service      | Eureka Server              | 8761  | —              | fait     |
| api-gateway            | Spring Cloud Gateway (WebFlux) | 8080 | —          | fait (JWT vérifié) |
| auth-service           | Spring Boot + Security/JWT | 8081  | auth_db        | fait     |
| claim-service          | Spring Boot (OpenFeign plus tard) | 8082 | claim_db | fait (sans IA) |
| notification-service   | Spring Boot                | 8083  | notification_db| à faire  |
| stats-service          | Spring Boot                | 8084  | stats_db       | à faire  |
| ai-classifier-service  | Python / FastAPI / scikit-learn | 8000 | —        | à faire  |
| frontend               | React + Vite + Tailwind    | 5173  | —              | fait (auth, admin, réclamations) |

Communication : REST synchrone (OpenFeign) claim-service → ai-classifier-service avec timeout + repli ;
événements asynchrones RabbitMQ pour notifications et statistiques.

## API Gateway (point d'entrée unique, seul service appelé par le frontend)
- Starter réactif `spring-cloud-starter-gateway-server-webflux` (nom Spring Cloud 2025.0) ;
  **ne jamais ajouter `spring-boot-starter-web`** au module (incompatible). Préfixe des propriétés :
  `spring.cloud.gateway.server.webflux.*` (l'ancien `spring.cloud.gateway.*` est déprécié).
- Routes et CORS dans `config-repo/api-gateway.yml`, avec `lb://<nom Eureka>` :

  | Chemin            | Service cible      |
  |-------------------|--------------------|
  | `/api/auth/**`    | `lb://auth-service`  |
  | `/api/claims/**`  | `lb://claim-service` |
  | `/api/stats/**`   | `lb://stats-service` |

  Le chemin est transmis **tel quel** (pas de StripPrefix) ; le JWT est vérifié avant le routage
  (voir « Sécurité à la passerelle »). Les contrôleurs des services exposent
  donc `/api/auth/...`, `/api/claims/...`, `/api/stats/...`. Le `spring.application.name` de chaque
  service doit correspondre au nom après `lb://`. Service absent d'Eureka → 503 propre.
- CORS **uniquement à la passerelle** (origines http://localhost:3000 et http://localhost:5173) ;
  ne pas configurer de CORS dans les services en aval (DedupeResponseHeader en filet de sécurité).
- `RequestLoggingFilter` (WebFilter, avant la sécurité) : log `MÉTHODE chemin -> statut (durée)` ;
  n'écrit JAMAIS d'en-tête, de query string ni de jeton (vérifié par un test).
- Import Config Server **non optionnel** (sans config, pas de routes) → lancer config-server avant ;
  `.env` importé comme dans auth-service (pour `JWT_SECRET`).
- Tests : `src/test/resources/application.yml` remplace la config principale (pas de Config Server,
  d'Eureka ni de .env ; secret JWT de test), charge `../config-repo/api-gateway.yml`, simule les
  instances avec `spring.cloud.discovery.client.simple.instances.*` + WireMock ; jetons de test
  fabriqués par `TestTokens` (valide, expiré, falsifié, mauvais émetteur).

### Sécurité à la passerelle (package `gateway.security`)
- `SecurityConfig` : Spring Security réactif (starter oauth2-resource-server, Nimbus), sans état.
  `NimbusReactiveJwtDecoder` HS256 + validateurs expiration (tolérance 60 s) et émetteur.
- Règles dans `config-repo/api-gateway.yml` (`gateway.security`, record `GatewaySecurityProperties`) :
  - toujours public : `OPTIONS` (pré-requêtes CORS) ;
  - `public-routes` (sans jeton) : `POST /api/auth/register`, `POST /api/auth/login`,
    `GET /actuator/health/**` (règles par méthode : `GET /api/auth/login` exige un jeton) ;
  - `role-rules` (jeton + rôle, sinon 403), évaluées dans l'ordre : `/api/auth/admin/**` → ADMIN ;
    `/api/claims/admin/**` → ADMIN ; `/api/claims/categories/**` et `/api/claims/services/**` →
    AGENT, ADMIN ; `/api/stats/**` → AGENT, ADMIN prévu en commentaire (à activer avec stats-service) ;
  - tout le reste (dont `/actuator/info`) : jeton valide, sinon 401.
  Pour une nouvelle règle : ajouter une entrée dans le YAML (rien à coder).
- `JsonSecurityErrorHandler` : 401/403 au format `ApiError` d'auth-service, mêmes messages
  (`Authentification requise`, `Jeton invalide ou expiré`, `Accès refusé : droits insuffisants`),
  en-tête `WWW-Authenticate: Bearer`.
- CORS : Spring Security réutilise `GlobalCorsProperties` (même config que la gateway) pour que
  les 401/403 portent les en-têtes CORS (sinon illisibles par le frontend).
- `UserHeadersFilter` (GlobalFilter, toutes les routes) : supprime TOUT en-tête `X-User-*` envoyé
  par le client, puis ajoute `X-User-Id` (sub), `X-User-Email`, `X-User-Role` depuis le jeton
  validé. `Authorization` est conservé. Les futurs services peuvent lire ces en-têtes, mais
  doivent garder leurs propres contrôles (défense en profondeur ; ils ne sont pas exposés
  directement, seule la gateway l'est).

## auth-service (port 8081, base auth_db)
- Packages : `user` (entité `User`, enum `Role`, dépôt), `service` (`AuthService`, `UserService`),
  `security` (`SecurityConfig`, `JwtProperties`, `TokenService`), `web` (contrôleurs, `dto/Dtos`),
  `error` (`ApiError`, `ApiException`, `GlobalExceptionHandler`, `JsonSecurityErrorHandler`),
  `bootstrap` (`AdminAccountInitializer`).
- Endpoints (tous sous `/api/auth`, publics = register et login) :
  | Méthode + chemin                          | Accès   | Rôle |
  |-------------------------------------------|---------|------|
  | `POST /api/auth/register`                 | public  | inscription CLIENT (rôle imposé), 201 |
  | `POST /api/auth/login`                    | public  | `{accessToken, tokenType, expiresIn, user}` |
  | `GET /api/auth/me`                        | connecté | profil |
  | `PUT /api/auth/me`                        | connecté | `{name?, currentPassword?, newPassword?}` |
  | `GET /api/auth/admin/users`               | ADMIN   | liste |
  | `POST /api/auth/admin/users`              | ADMIN   | crée AGENT ou ADMIN (CLIENT refusé, 400) |
  | `PATCH /api/auth/admin/users/{id}/role`   | ADMIN   | `{role}` (pas sur soi-même → 409) |
  | `PATCH /api/auth/admin/users/{id}/status` | ADMIN   | `{active}` (pas sur soi-même → 409) |
- Erreurs : JSON unique `ApiError {timestamp, status, error, message, path, fieldErrors?}` pour
  400/401/403/404/409/500, y compris les 401/403 de Spring Security (`JsonSecurityErrorHandler`).
  Login : même message pour email inconnu et mauvais mot de passe (+ BCrypt factice contre le timing) ;
  compte désactivé → 403, signalé seulement si le mot de passe est correct.
- Schéma : Flyway (`src/main/resources/db/migration/V<n>__*.sql`, compatible PostgreSQL ET H2) ;
  Hibernate en `ddl-auto: validate`. Ne jamais modifier une migration déjà appliquée : en créer une nouvelle.
- Admin initial : créé au démarrage depuis `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` seulement
  s'il n'existe AUCUN admin en base.
- Tests : `src/test/resources/application.yml` (H2 mode PostgreSQL, pas de Config Server/Eureka/.env),
  tests unitaires Mockito (`service/`) et intégration MockMvc (`web/AuthApiIntegrationTest`).

## claim-service (port 8082, base claim_db) — étape 5b, SANS IA
- Packages : `domain` (entités + dépôts : `Claim`, `ClaimStatus`, `ClaimWorkflow`, `Category`,
  `ProcessingService` = « Service » du diagramme (nom évitant le conflit avec `@Service`),
  `ServiceAgent`, `Comment`, `StatusHistory`, `Attachment`), `access` (`ClaimAccessPolicy` logique
  pure, `ClaimAccess` chargement 404 + Specification de liste), `service` (`ClaimService`,
  `ReferenceDataService`, `AttachmentService`, `DtoMapper`), `storage` (`AttachmentStorage`,
  `AllowedFileType`, `FileNameSanitizer`, `AttachmentProperties`), `event` (`ClaimEventPublisher`,
  `LoggingClaimEventPublisher`, `ClaimEventDispatcher` après commit), `security` (`CurrentUser`,
  `CurrentUserArgumentResolver`, `UserRole`), `web` (`ClaimController`, `ReferenceDataController`,
  `dto/Dtos`), `error` (`ApiError` identique à auth-service).
- **Pas de Spring Security** : appelant = en-têtes `X-User-Id/Email/Role` de la gateway (absents ou
  invalides → 401). Limite documentée : le port 8082 ne doit jamais être exposé.
- Statuts (diagramme d'états, `ClaimStatus.canTransitionTo` + `ClaimWorkflow`) :
  NEW→CLASSIFIED (ADMIN, via `PUT /{id}/classification`), NEW→REJECTED (ADMIN), CLASSIFIED→ASSIGNED
  (AGENT/ADMIN, via `PUT /{id}/assignment` avec agentId), ASSIGNED→IN_PROGRESS et IN_PROGRESS→RESOLVED
  (AGENT/ADMIN), RESOLVED→CLOSED et RESOLVED→IN_PROGRESS (CLIENT propriétaire SEULEMENT).
  Transition absente → 409 ; existante mais rôle interdit → 403. Tout changement → `StatusHistory`
  (création = null→NEW). `PATCH /status` refuse CLASSIFIED/ASSIGNED (400 : endpoints dédiés).
- Décisions validées par l'utilisateur : tri des NEW par l'ADMIN seul ; réaffectation (ASSIGNED,
  IN_PROGRESS) = statut CONSERVÉ, agent obligatoire, trace en commentaire interne ; correction de
  classification = statut conservé, source MANUAL, trace interne.
- Visibilité : CLIENT = ses réclamations ; AGENT = service ∈ ses services (table `service_agents`)
  OU assignedAgentId = lui ; ADMIN = tout. Non visible → **404 partout** (jamais 403). Commentaires
  internes filtrés EN BASE pour le client. Action interdite au rôle sur réclamation visible → 403.
- Agents : l'admin saisit `agentId` (id auth-service) + `agentName` ; pas d'appel à auth-service (limite
  documentée). L'agent affecté doit être rattaché au service cible (400 sinon).
- Pièces jointes : PDF/PNG/JPG par signature binaire + extension cohérente, 5 Mo (400), 5 par
  réclamation (409), réclamation ouverte (409 si CLOSED/REJECTED). Stockage `CLAIMS_STORAGE_DIR`
  (défaut `~/reclamations-data/attachments`, hors dépôt) sous `<claimId>/<uuid>.<ext>` ; fichier
  supprimé si la transaction échoue. Téléchargement : mêmes règles d'accès (404).
- Événements : `ClaimEventPublisher` appelé après commit (CREATED, STATUS_CHANGED,
  CLASSIFICATION_CHANGED, ASSIGNED, PUBLIC_COMMENT_ADDED) ; implémentation actuelle = log. Pour
  RabbitMQ : nouvelle implémentation `@Primary`, ne pas toucher `ClaimService`.
- Données initiales (Flyway V2) : services et catégories Facturation, Support technique, Livraison.
  `Claim` a un `@Version` (conflit concurrent → 409).
- Endpoints : voir README « API des réclamations ». Tests : `ClaimWorkflowTest`,
  `ClaimAccessPolicyTest`, `FileValidationTest`, `ClaimApiIntegrationTest` (MockMvc, en-têtes simulés).
- À venir (étape IA) : classification automatique à la création (source AI + score, repli MANUAL),
  classe Prediction (BF18, BF20).

## Frontend (`frontend/`, port 5173)
- Vite 8, React 19, react-router 7.18 (pas 8 : exige Node ≥ 22.22, la machine a 22.20), Tailwind CSS 4
  (plugin `@tailwindcss/vite`, classes réutilisables `btn-*`, `input`, `card` en `@utility` dans
  `src/index.css`). JavaScript (pas TypeScript). `fetch` natif, pas d'axios. Versions EXACTES dans
  package.json. Tests : Vitest 5 + Testing Library + jsdom 29 (jsdom 31 exige Node ≥ 22.22).
- Lancement : `cd frontend; npm install; npm run dev` ; `npm test` ; `npm run build`.
- `VITE_API_URL` (défaut http://localhost:8080, modèle `frontend/.env.example`) : le frontend
  n'appelle QUE la gateway.
- Structure `src/` : `api/client.js` (fetch + `ApiError` + 401 centralisé ; `body` FormData =
  multipart sans Content-Type forcé ; `responseType: 'blob'` = téléchargement), `api/authApi.js` et
  `api/claimsApi.js` (endpoints, alignés sur les Dtos.java), `auth/tokenStorage.js` (SEUL accès au
  jeton), `auth/AuthContext.jsx` (user lu via GET /me), `auth/RouteGuards.jsx` (`ProtectedRoute
  roles`, `GuestOnlyRoute`, `HomeRedirect`), `navigation.js` (menu par rôle ; `exclude` = sous-chemin
  qui ne surligne pas l'entrée), `claims/` (`claimRules.js`, `attachmentRules.js`, `errors.js`),
  `components/` (+ `components/claims/`), `pages/`, `utils/` (validation identique au backend,
  formats date/heure/taille), `test/` (setup, `renderApp`, `mockApi`, données `claimDetail`,
  `assignedClaim`, `REFERENCE_HANDLERS`).
- Routes : `/connexion`, `/inscription`, `/profil`, `/reclamations` et `/reclamations/:id` (tous
  les rôles, contenu selon le rôle), `/reclamations/nouvelle` (CLIENT), `/admin/utilisateurs` et
  `/admin/referentiels` (ADMIN), `/403`, `*` (404).
- Réclamations (étape 5c) :
  - une seule liste et un seul détail pour les 3 rôles ; filtre statut + page dans l'URL
    (`?statut=&page=`, 10 par page). Seul filtre serveur disponible : le statut (limite signalée).
  - `claims/claimRules.js` = copie de `ClaimStatus`/`ClaimWorkflow`/`ClaimService` (transitions par
    rôle, qui peut classer / corriger / affecter, commentaires internes, complément). Les actions
    interdites sont MASQUÉES (pas tentées) ; à mettre à jour si le workflow serveur change.
    CLASSIFIED/ASSIGNED passent par les formulaires dédiés, jamais par PATCH /status.
  - client : jamais de référentiel (la gateway répondrait 403), jamais de commentaire interne
    (filtré par le serveur ET par `visibleComments`) ; boutons Clôturer/Rouvrir seulement en RESOLVED.
  - détail : `perform(action, texte)` centralise succès/erreur ; 409 ou 404 → rechargement ;
    messages « Action non autorisée » (403), « Introuvable » (404), « Action impossible » (409) ;
    400 par champ via `err.fieldErrors`. Panneaux réinitialisés par `key` (updatedAt + statut).
  - pièces jointes : contrôle client (extension + type MIME, 5 Mo, 5 max) ; téléchargement par
    fetch authentifié → Blob → lien `blob:` temporaire (jamais de jeton dans une URL).
  - auteurs des commentaires et de l'historique : rôle seulement (« Vous » pour soi), le backend
    ne renvoie pas de nom (limite signalée).
  - admin référentiels : agents à rattacher = comptes AGENT actifs de `GET /api/auth/admin/users`.
- Règles : rôle uniquement depuis GET /api/auth/me (jamais décodé du JWT) ; tout 401 reçu AVEC un
  jeton → déconnexion + /connexion (« session expirée ») ; un 401 sans jeton (mauvais mot de passe)
  est juste affiché. Erreurs : `err.message` + `err.fieldErrors` (champ → message) ; 409 → champ email.
- Jeton en `sessionStorage` (compromis documenté dans tokenStorage.js et le README : survit au
  rechargement, effacé à la fermeture de l'onglet, mais lisible en cas de XSS ; alternative =
  cookie HttpOnly, nécessite de modifier le backend).
- Futures pages (statistiques) : ajouter la route dans `AppRoutes.jsx` et passer l'entrée
  correspondante de `navigation.js` à `available: true` avec un `to`.

## JWT (contrat partagé entre auth-service et la gateway)
- HS256 ; clé = octets UTF-8 de `JWT_SECRET` (≥ 32 caractères, aucune valeur par défaut).
- `security.jwt.secret` et `security.jwt.issuer` sont dans `config-repo/application.yml` (PARTAGÉ) ;
  `security.jwt.expiration` (`JWT_EXPIRATION`, défaut 1h) dans `config-repo/auth-service.yml`.
- Claims : `iss` = `reclamations-auth-service`, `sub` = id utilisateur, `email`, `role`
  (CLIENT | AGENT | ADMIN → autorité `ROLE_<role>`), `iat`, `exp`.
- Vérifié à la gateway (`NimbusReactiveJwtDecoder`) ET dans auth-service (`NimbusJwtDecoder`).
- **Limite connue — pas de révocation** : la gateway ne consulte pas la base. Un compte désactivé
  (ou dont le rôle a changé) garde un jeton valide jusqu'à `exp` (`JWT_EXPIRATION`, 1h par défaut) ;
  seul auth-service le bloque sur `/me`. Pistes si besoin : durée courte, liste de révocation.
- Ne jamais afficher `JWT_SECRET` (logs, tests, doc) ni lire `.env` à voix haute.

## Variables d'environnement (.env à la racine, modèle .env.example)
- Lecture : Docker Compose lit `.env` ; les services Spring l'importent avec
  `spring.config.import: optional:file:../.env[.properties]` (et `./.env` depuis l'IDE).
  Les variables d'environnement du système restent prioritaires.
- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` (base par défaut de l'image), `POSTGRES_PORT`,
  `POSTGRES_HOST` (facultatif, défaut localhost), `JWT_SECRET`, `JWT_EXPIRATION`, `ADMIN_EMAIL`,
  `ADMIN_PASSWORD`, `ADMIN_NAME`, `CLAIMS_STORAGE_DIR` (facultatif, pièces jointes, hors dépôt).
- Ne jamais demander ces valeurs dans le chat ni les écrire dans `.env` : l'utilisateur remplit `.env`.

## Stack et versions
- Spring Boot **3.5.16**, Spring Cloud **2025.0.3** (gérés dans le `pom.xml` parent).
- Java : cible **17** (`maven.compiler.release=17`) ; JDK 25 utilisé pour compiler/lancer (compatible).
- Maven via le wrapper `mvnw.cmd` (Maven 3.9.16). PostgreSQL 16, RabbitMQ.

## Exécution locale (exigé par le cahier des charges v2 : §6 Portabilité, §9 Outils, §11 Livrables)
- **Docker Compose sert uniquement aux bases de données et au broker de messages** (PostgreSQL,
  plus tard RabbitMQ), jamais aux services. Variables dans `.env` (modèle : `.env.example`, non versionné).
- **Les services ne sont PAS conteneurisés** (aucun Dockerfile de service) : ils s'exécutent
  directement avec Maven (`mvnw spring-boot:run` ou l'IDE), Python (uvicorn) et Node (npm).
- Livrable : « application exécutable en local, avec un guide de lancement (README) » → tenir
  `README.md` à jour à chaque nouveau service (commande de lancement, port, URL de vérification).
- Ordre de démarrage : `docker compose up -d postgres` → config-server → discovery-service →
  api-gateway → auth-service → claim-service → autres services → frontend (`npm run dev`).
- Une base PostgreSQL par service, créée par `docker/postgres/init/01-create-databases.sql` (une
  ligne par service ; script exécuté seulement si le volume est vide). Volume existant : rejouer le
  script, idempotent : `docker exec reclamations-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER
  -d postgres -f /docker-entrypoint-initdb.d/01-create-databases.sql'` (auth_db et claim_db existent).
  Un seul utilisateur PostgreSQL (`POSTGRES_USER`) pour toutes les bases en développement.

## Conventions
- Monorepo : un dossier par service à la racine, modules Maven déclarés dans le `pom.xml` parent.
- groupId `ma.emsi.reclamations` ; package racine `ma.emsi.reclamations.<service>` (sans tiret,
  ex. `configserver`, `discovery`, `auth`, `claim`, `notification`, `stats`, `gateway`).
- Artefact = nom du dossier (`<finalName>` = artifactId).
- Configuration : `application.yml` minimal dans chaque service (port, nom, import du Config Server) ;
  le reste dans `config-repo/<nom-du-service>.yml` ; `config-repo/application.yml` = commun à tous.
  Import : `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`
  (sans `optional:` pour les services qui ne peuvent pas fonctionner sans leur config, ex. api-gateway).
- Versions de dépendances non gérées par Spring Boot / Spring Cloud : propriété dans le pom du module
  (ex. `wiremock.version` 3.13.2 dans api-gateway, tests uniquement).
- Valeurs sensibles via variables d'environnement, jamais en dur dans le dépôt.
- Chaque module a au moins un test `@SpringBootTest` (contexte qui démarre) ; `mvnw clean verify` doit passer.
- Tests sans infrastructure : un `src/test/resources/application.yml` remplace la config principale
  (Config Server, Eureka désactivés ; H2 mode PostgreSQL pour les services avec base).
- Ne jamais committer : l'utilisateur fait ses commits lui-même.
- Code et commentaires en français pour la doc, identifiants en anglais.

## Structure du dépôt
```
pom.xml              POM parent (Spring Boot parent + BOM Spring Cloud, liste des modules)
mvnw, mvnw.cmd, .mvn Maven Wrapper
config-repo/         configurations centralisées servies par config-server
config-server/       Spring Cloud Config Server (profil native)
discovery-service/   serveur Eureka
api-gateway/         Spring Cloud Gateway (réactif) — routes, CORS, journalisation
auth-service/        comptes, login JWT, rôles, administration des utilisateurs
claim-service/       réclamations, statuts, commentaires, affectations, pièces jointes
frontend/            React (Vite, Tailwind) — npm, hors Maven
docs/                cahier des charges et conception
scripts/             scripts de test manuels (test-claim-service.ps1, PowerShell 5.1, via la gateway)
docker/postgres/init scripts SQL de création des bases (une par service)
docker-compose.yml   PostgreSQL (puis RabbitMQ) uniquement
.env.example         modèle des variables Docker Compose
```

## Ordre de développement prévu (cahier des charges §12)
1. ✅ Socle : config-server, discovery-service, docker-compose (PostgreSQL).
2. ✅ api-gateway (routes, CORS, logs) ; ✅ auth-service (BF1–BF5, BF21) ; ✅ vérification JWT à la gateway.
3. ✅ claim-service sans IA (BF6–BF15, BF22, BF23) ; notification-service (BF10), stats-service
   (BF24–BF26) + RabbitMQ.
4. ai-classifier-service + jeu de données + notebook d'évaluation (BF16–BF20, objectif ≥ 85 % accuracy / F1 macro).
5. Frontend React : ✅ 5a (connexion, inscription, profil, admin utilisateurs) ; ✅ 5c (pages
   réclamations client/agent/admin, référentiels) ; à venir : tableau de bord (stats-service).
6. Tests d'intégration, charge, évaluation du modèle.
7. Documentation (OpenAPI/Swagger), rapport, soutenance.

## Environnement de la machine (Windows)
- Les variables JAVA_HOME / MAVEN_HOME de l'utilisateur pointaient vers des dossiers supprimés de
  Downloads, et `java` du PATH est un Java 8. JDK 25 disponible : `C:\Users\stert\.jdks\openjdk-25`.
- Toujours utiliser `mvnw.cmd` (pas besoin de Maven dans le PATH) avec un JAVA_HOME valide.
