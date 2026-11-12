# Plateforme intelligente de gestion des réclamations clients

PFA 2026-2027 — architecture microservices (Spring Boot / Spring Cloud) et classification par IA.
Cahier des charges : [`docs/cahier-des-charges.pdf`](docs/cahier-des-charges.pdf).

**État actuel :** `config-server` (8888), `discovery-service` / Eureka (8761), `api-gateway` (8080),
`auth-service` (8081, PostgreSQL `auth_db`), `claim-service` (8082, PostgreSQL `claim_db` :
réclamations, statuts, commentaires, affectations, pièces jointes — classement manuel, l'IA viendra
plus tard) et le **frontend React** (5173 : connexion, inscription, profil, gestion des utilisateurs).

**Mode d'exécution :** l'application s'exécute en local. Docker Compose ne lance que les bases de
données et le broker de messages ; les services tournent directement avec Maven (et plus tard
Python et Node), sans conteneur.

## Prérequis (Windows)
- JDK 17 ou plus récent (testé avec JDK 25), avec `JAVA_HOME` pointant dessus.
- Docker Desktop (uniquement pour PostgreSQL).
- Maven n'est pas nécessaire : le projet fournit le wrapper `mvnw.cmd`.
- Node.js ≥ 22.12 et npm (testé avec Node 22.20 / npm 10.9) pour le frontend.

Vérifier / corriger `JAVA_HOME` dans PowerShell (pour la session en cours) :
```powershell
$env:JAVA_HOME = "C:\Users\stert\.jdks\openjdk-25"
& "$env:JAVA_HOME\bin\java" -version
```
Pour le rendre permanent : *Paramètres système > Variables d'environnement*.

## Configuration locale : fichier `.env`
Le fichier `.env` (à la racine, jamais versionné) est lu par Docker Compose **et** par les services
Spring. Partir du modèle :
```powershell
Copy-Item .env.example .env     # si .env n'existe pas encore
notepad .env
```

| Variable          | Rôle                                                                  |
|-------------------|-----------------------------------------------------------------------|
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | identifiants PostgreSQL (conteneur + services)      |
| `POSTGRES_DB`     | base par défaut de l'image (`postgres`) ; les bases des services sont créées par `docker/postgres/init/` |
| `POSTGRES_PORT`   | port exposé (5432 ; changer si un PostgreSQL local occupe déjà ce port) |
| `JWT_SECRET`      | **obligatoire**, au moins 32 caractères, partagé par auth-service et la gateway (signature / vérification) |
| `JWT_EXPIRATION`  | durée de validité des jetons (`1h` par défaut)                        |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | compte ADMIN créé au 1er démarrage d'auth-service s'il n'existe aucun admin |
| `CLAIMS_STORAGE_DIR` | facultatif : dossier des pièces jointes de claim-service, **hors du dépôt** (défaut `<dossier utilisateur>/reclamations-data/attachments` ; écrire le chemin avec des `/`) |

Générer un `JWT_SECRET` aléatoire (à coller dans `.env`) :
```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```
Format du fichier : `CLE=valeur`, sans guillemets ni espaces autour de `=`, sans `\`.

## Compiler et tester
```powershell
.\mvnw.cmd clean verify
```
Les tests n'ont besoin ni de Docker ni des autres services (H2 en mémoire pour auth-service et
claim-service ; les en-têtes de la gateway sont simulés dans les tests de claim-service).
Frontend : `cd frontend; npm test; npm run build`.

## Lancer la plateforme (dans cet ordre)
Attendre que chaque service affiche `Started ...Application` avant de lancer le suivant.

**0 — PostgreSQL** (Docker Compose)
```powershell
docker compose up -d postgres
docker compose ps               # STATUS : healthy
```

**Terminal 1 — Config Server**
```powershell
.\mvnw.cmd -pl config-server spring-boot:run
```
Vérifier : http://localhost:8888/sample-service/default

**Terminal 2 — Eureka**
```powershell
.\mvnw.cmd -pl discovery-service spring-boot:run
```
Tableau de bord : http://localhost:8761

**Terminal 3 — API Gateway** (le Config Server fournit les routes ; `JWT_SECRET` requis dans `.env`)
```powershell
.\mvnw.cmd -pl api-gateway spring-boot:run
```
Vérifier : http://localhost:8080/actuator/health → `{"status":"UP"}`

**Terminal 4 — auth-service** (PostgreSQL et `.env` requis)
```powershell
.\mvnw.cmd -pl auth-service spring-boot:run
```
Au premier démarrage, Flyway crée la table `users` et le compte ADMIN initial est créé
(log `Compte ADMIN initial créé`). `AUTH-SERVICE` apparaît dans Eureka en ~30 s.

**Terminal 5 — claim-service** (PostgreSQL avec la base `claim_db`, voir
[PostgreSQL](#postgresql-docker-compose) si votre volume existait avant cette étape)
```powershell
.\mvnw.cmd -pl claim-service spring-boot:run
```
Vérifier : http://localhost:8082/actuator/health → `{"status":"UP"}` (vérification locale
uniquement : en dehors de ce test, on passe toujours par la gateway). Flyway crée le schéma et les
catégories / services d'exemple ; `CLAIM-SERVICE` apparaît dans Eureka en ~30 s.

Depuis IntelliJ : lancer `ConfigServerApplication`, `DiscoveryServiceApplication`,
`ApiGatewayApplication`, `AuthServiceApplication` puis `ClaimServiceApplication` (répertoire de
travail = racine du projet).

**Terminal 6 — Frontend React** (une fois la gateway et auth-service démarrés)
```powershell
cd frontend
npm install        # la première fois seulement
npm run dev
```
Ouvrir http://localhost:5173 (port imposé : c'est l'origine autorisée par le CORS de la gateway).

## Frontend (`frontend/`)
Vite + React 19 + React Router 7 + Tailwind CSS 4, en JavaScript. Le frontend n'appelle **que**
l'API Gateway.

| Commande          | Rôle |
|-------------------|------|
| `npm run dev`     | serveur de développement sur http://localhost:5173 |
| `npm test`        | tests Vitest + Testing Library (aucun backend nécessaire : API simulée) |
| `npm run build`   | build de production dans `frontend/dist/` |

**Variable d'environnement** : `VITE_API_URL` (défaut `http://localhost:8080`). Pour la changer,
copier `frontend/.env.example` en `frontend/.env`.

**Pages** :

| Route                     | Rôles  | Contenu |
|---------------------------|--------|---------|
| `/connexion`, `/inscription` | public | validation côté client (email valide, mot de passe de 8 caractères minimum) |
| `/profil`                 | tous   | modifier son nom, changer son mot de passe en saisissant l'ancien |
| `/reclamations`           | tous   | liste paginée (10 par page) filtrable par statut (`?statut=...&page=...` dans l'URL) ; client : « Mes réclamations » ; agent : réclamations de ses services ou affectées (badge « vous ») ; admin : toutes, bouton « Nouvelles à classer » |
| `/reclamations/nouvelle`  | CLIENT | titre, description, pièces jointes facultatives (pas de catégorie : le classement est fait ensuite) |
| `/reclamations/:id`       | tous   | détail, historique, commentaires, pièces jointes et actions selon le rôle (voir ci-dessous) |
| `/admin/referentiels`     | ADMIN  | services et catégories (création, modification, suppression), agents rattachés à chaque service (choisis parmi les comptes AGENT actifs) |
| `/admin/utilisateurs`     | ADMIN  | liste, création d'un agent ou admin, changement de rôle, activation/désactivation |
| `/403`, `*`               | —      | accès refusé, page introuvable |

La barre de navigation s'adapte au rôle (« Statistiques » est encore marquée « Bientôt »).

**Règles d'affichage des réclamations** (`src/claims/claimRules.js`, copie des règles du
claim-service : les actions interdites sont masquées, le serveur revérifie tout) :

| Action sur le détail | Client (propriétaire) | Agent | Admin |
|----------------------|-----------------------|-------|-------|
| Commentaire public / interne | public seulement ; ne voit jamais les internes | les deux (internes sur fond ambré, badge « Interne ») | les deux |
| Complément d'information (BF9) | réclamation ouverte | — | — |
| Classer (NEW → CLASSIFIED) | — | — | oui |
| Rejeter (NEW → REJECTED) | — | — | oui |
| Corriger catégorie / service | — | CLASSIFIED, ASSIGNED, IN_PROGRESS | idem |
| Affecter (CLASSIFIED, agent facultatif) / réaffecter (ASSIGNED, IN_PROGRESS, agent obligatoire) | — | oui | oui |
| Prendre en charge (ASSIGNED → IN_PROGRESS), Marquer comme résolue (IN_PROGRESS → RESOLVED) | — | oui | oui |
| Clôturer / Rouvrir (RESOLVED → CLOSED / IN_PROGRESS) | oui | — | — |
| Pièces jointes | ajout tant que la réclamation est ouverte et qu'il y en a moins de 5 ; téléchargement toujours | idem | idem |

Erreurs : message du serveur précédé de sa nature (« Action non autorisée » 403, « Introuvable »
404, « Action impossible » 409) ; les 400 de validation s'affichent sous chaque champ. Après un
409 ou un 404, la réclamation est rechargée (son état a pu changer entre-temps). Une réclamation
non visible affiche « Réclamation introuvable ».

**Pièces jointes** : contrôles côté client avant envoi (PDF, PNG ou JPG, 5 Mo par fichier,
5 par réclamation ; le serveur vérifie en plus la signature du fichier). Le téléchargement se fait
par `fetch` avec l'en-tête `Authorization`, puis enregistrement du fichier reçu (`Blob`) : le jeton
n'apparaît jamais dans une URL. À la création, la réclamation est d'abord enregistrée puis les
fichiers envoyés ; si l'envoi échoue, la page de détail le signale et permet de réessayer.

**Authentification** :
- le jeton est stocké à un seul endroit (`src/auth/tokenStorage.js`) et ajouté automatiquement
  à chaque appel (`src/api/client.js`) ;
- le rôle est lu depuis `GET /api/auth/me` (profil renvoyé par le serveur), jamais décodé du jeton ;
  il ne sert qu'à l'affichage et à la navigation : la vraie protection reste la gateway et l'auth-service ;
- tout 401 reçu avec un jeton (expiré, invalide) déconnecte et renvoie vers la connexion.

**Compromis sur le stockage du jeton** : `sessionStorage`. Le jeton survit au rechargement de la
page et disparaît à la fermeture de l'onglet, mais il reste lisible par du JavaScript : une faille
XSS permettrait de le voler (React échappe l'affichage par défaut, ce qui limite ce risque).
L'alternative plus sûre — un cookie `HttpOnly` posé par le serveur, avec protection CSRF —
demanderait de modifier l'auth-service et la gateway ; elle pourra être adoptée plus tard en ne
changeant que `tokenStorage.js` et `client.js` côté frontend.

**Structure** :
```
frontend/
  index.html, vite.config.js, package.json, .env.example
  src/
    main.jsx, AppRoutes.jsx      point d'entrée et plan des routes
    config.js, navigation.js     URL de l'API, libellés des rôles, menu par rôle
    api/                         client HTTP (client.js) et endpoints (authApi.js, claimsApi.js)
    auth/                        jeton, contexte d'authentification, routes protégées
    claims/                      règles d'affichage par rôle et statut, pièces jointes, messages d'erreur
    components/                  mise en page, barre de navigation, champs, alertes
    components/claims/           badge de statut, pagination, historique, commentaires, pièces jointes,
                                 actions de statut, classement, affectation, complément
    pages/                       connexion, inscription, profil, réclamations (liste, création,
                                 détail), admin (utilisateurs, référentiels), 403/404
    utils/                       validation et formatage
    test/                        tests Vitest et utilitaires
```

## Routes de l'API Gateway (port 8080)
Le frontend n'appelle que la passerelle. Routes définies dans `config-repo/api-gateway.yml` :

| Chemin           | Service (nom Eureka) | État                |
|------------------|----------------------|---------------------|
| `/api/auth/**`   | auth-service         | disponible          |
| `/api/claims/**` | claim-service        | disponible          |
| `/api/stats/**`  | stats-service        | à venir → 503       |

CORS autorisé pour le frontend React : http://localhost:3000 et http://localhost:5173.
Chaque requête est tracée dans la console de la gateway : `GET /api/auth/me -> 200 (12 ms)`
(méthode, chemin, statut, durée ; jamais le jeton ni les en-têtes).

### Sécurité à la passerelle (JWT)
La gateway vérifie le jeton (signature HS256 avec `JWT_SECRET`, expiration, émetteur) **avant**
de router la requête. Règles dans `config-repo/api-gateway.yml` (`gateway.security`) :

| Accès                    | Routes |
|--------------------------|--------|
| Public (sans jeton)      | `POST /api/auth/register`, `POST /api/auth/login`, `GET /actuator/health`, toutes les pré-requêtes CORS `OPTIONS` |
| Rôle ADMIN               | `/api/auth/admin/**`, `/api/claims/admin/**` (sinon **403**) |
| Rôle AGENT ou ADMIN      | `/api/claims/categories/**`, `/api/claims/services/**` (sinon **403**) |
| Prévu (commenté)         | `/api/stats/**` → AGENT ou ADMIN, à activer avec stats-service |
| Jeton valide             | tout le reste, ex. `GET/PUT /api/auth/me`, le reste de `/api/claims/**` (sinon **401**) |

Erreurs au même format JSON que auth-service :
`{"timestamp":…,"status":401,"error":"Unauthorized","message":"Authentification requise","path":"/api/auth/me"}`
(messages : `Authentification requise`, `Jeton invalide ou expiré`, `Accès refusé : droits insuffisants`).

**En-têtes transmis aux services** (requêtes authentifiées) : `X-User-Id` (id utilisateur),
`X-User-Email`, `X-User-Role`, plus l'en-tête `Authorization` d'origine. Tout en-tête `X-User-*`
envoyé par le client est **supprimé** : impossible d'usurper une identité ou un rôle.

**Limite connue — pas de révocation des jetons :** la gateway ne consulte pas la base. Un compte
désactivé, ou dont le rôle a changé, garde un jeton valide jusqu'à son expiration
(`JWT_EXPIRATION`, 1h par défaut). auth-service refuse toutefois `/api/auth/me` à un compte désactivé.

## API d'authentification (auth-service, via la gateway)

| Méthode | Chemin                               | Accès     | Description |
|---------|--------------------------------------|-----------|-------------|
| POST    | `/api/auth/register`                 | public    | inscription d'un client (rôle CLIENT imposé) |
| POST    | `/api/auth/login`                    | public    | renvoie `accessToken` (JWT HS256), `expiresIn`, `user` |
| GET     | `/api/auth/me`                       | connecté  | profil de l'utilisateur connecté |
| PUT     | `/api/auth/me`                       | connecté  | `name`, et/ou `newPassword` avec `currentPassword` |
| GET     | `/api/auth/admin/users`              | ADMIN     | liste des utilisateurs |
| POST    | `/api/auth/admin/users`              | ADMIN     | créer un compte `AGENT` ou `ADMIN` |
| PATCH   | `/api/auth/admin/users/{id}/role`    | ADMIN     | `{"role":"AGENT"}` |
| PATCH   | `/api/auth/admin/users/{id}/status`  | ADMIN     | `{"active":false}` |

Erreurs au format JSON : `{timestamp, status, error, message, path, fieldErrors?}` —
400 (validation), 401 (non authentifié, identifiants incorrects), 403 (droits insuffisants,
compte désactivé), 404, 409 (email déjà utilisé).

### Tester avec Invoke-RestMethod (PowerShell)
```powershell
$G = "http://localhost:8080"
$json = "application/json; charset=utf-8"
# Affiche le code et le corps JSON d'une erreur (PowerShell 5.1 : le corps est dans ErrorDetails)
function Show-Error { param($e) "{0} {1}" -f [int]$e.Exception.Response.StatusCode, $e.ErrorDetails.Message }

# Inscription et connexion : publiques
Invoke-RestMethod -Method Post "$G/api/auth/register" -ContentType $json `
  -Body '{"name":"Client Test","email":"client@test.local","password":"motdepasse123"}'
$T = (Invoke-RestMethod -Method Post "$G/api/auth/login" -ContentType $json `
  -Body '{"email":"client@test.local","password":"motdepasse123"}').accessToken

# Sans jeton → 401 (réponse de la gateway)
try { Invoke-RestMethod "$G/api/auth/me" } catch { Show-Error $_ }
# Avec jeton → 200 + profil
Invoke-RestMethod "$G/api/auth/me" -Headers @{ Authorization = "Bearer $T" }
# Jeton falsifié → 401 « Jeton invalide ou expiré »
try { Invoke-RestMethod "$G/api/auth/me" -Headers @{ Authorization = "Bearer $($T.Substring(0, $T.Length - 4))AAAA" } } catch { Show-Error $_ }
# Client sur l'administration → 403, même en ajoutant un faux X-User-Role
try { Invoke-RestMethod "$G/api/auth/admin/users" -Headers @{ Authorization = "Bearer $T"; "X-User-Role" = "ADMIN" } } catch { Show-Error $_ }

# Admin (remplacer par les valeurs de votre .env) → 200 + liste
$A = (Invoke-RestMethod -Method Post "$G/api/auth/login" -ContentType $json `
  -Body '{"email":"VOTRE_ADMIN_EMAIL","password":"VOTRE_ADMIN_PASSWORD"}').accessToken
Invoke-RestMethod "$G/api/auth/admin/users" -Headers @{ Authorization = "Bearer $A" }
```

### Tester avec curl (PowerShell)
```powershell
$G = "http://localhost:8080"

# Inscription d'un client
curl.exe -s -X POST "$G/api/auth/register" -H "Content-Type: application/json" `
  -d '{\"name\":\"Client Test\",\"email\":\"client@test.local\",\"password\":\"motdepasse123\"}'

# Connexion → récupérer le jeton
$login = curl.exe -s -X POST "$G/api/auth/login" -H "Content-Type: application/json" `
  -d '{\"email\":\"client@test.local\",\"password\":\"motdepasse123\"}' | ConvertFrom-Json
$T = $login.accessToken

# Profil avec et sans jeton (200 puis 401)
curl.exe -s "$G/api/auth/me" -H "Authorization: Bearer $T"
curl.exe -s -i "$G/api/auth/me"

# Un client sur l'administration → 403
curl.exe -s -i "$G/api/auth/admin/users" -H "Authorization: Bearer $T"

# Admin initial (identifiants de .env) → liste des utilisateurs
$admin = curl.exe -s -X POST "$G/api/auth/login" -H "Content-Type: application/json" `
  -d "{\`"email\`":\`"$env:ADMIN_EMAIL\`",\`"password\`":\`"$env:ADMIN_PASSWORD\`"}" | ConvertFrom-Json
curl.exe -s "$G/api/auth/admin/users" -H "Authorization: Bearer $($admin.accessToken)"
```
(Pour la dernière commande, remplacer `$env:ADMIN_EMAIL` / `$env:ADMIN_PASSWORD` par les valeurs
de votre `.env`, ou les définir d'abord dans la session PowerShell.)

## API des réclamations (claim-service, via la gateway)
Tous les chemins sont sous `/api/claims`. claim-service identifie l'appelant par les en-têtes
`X-User-Id`, `X-User-Email`, `X-User-Role` posés par la gateway (absents → 401).

| Méthode | Chemin | Accès | Description |
|---------|--------|-------|-------------|
| POST  | `/api/claims` | CLIENT | soumettre `{title, description}` → 201, statut `NEW` (BF6) |
| GET   | `/api/claims?status=&page=0&size=20` | tous | réclamations **visibles**, plus récentes d'abord, `size` ≤ 100 (BF7, BF11) |
| GET   | `/api/claims/{id}` | tous | détail + historique + commentaires + pièces jointes (BF8) |
| POST  | `/api/claims/{id}/complements` | CLIENT propriétaire | `{text}` ajouté à la description, réclamation ouverte (BF9) |
| POST  | `/api/claims/{id}/comments` | tous | `{content, internal?}` ; `internal: true` réservé aux agents/admins (BF13) |
| PATCH | `/api/claims/{id}/status` | selon transition | `{status, note?}` (BF12) |
| PUT   | `/api/claims/{id}/classification` | ADMIN (NEW) ; AGENT/ADMIN (correction) | `{categoryId, serviceId?}` (BF14) |
| PUT   | `/api/claims/{id}/assignment` | AGENT/ADMIN | `{serviceId, agentId?}` (BF15, BF23) |
| POST  | `/api/claims/{id}/attachments` | tous (si visible) | multipart, partie `files` (un ou plusieurs fichiers) |
| GET   | `/api/claims/{id}/attachments/{attachmentId}` | tous (si visible) | téléchargement |
| GET   | `/api/claims/categories`, `/api/claims/services`, `/api/claims/services/{id}/agents` | AGENT, ADMIN | listes déroulantes |
| POST/PUT/DELETE | `/api/claims/admin/services[/{id}]` | ADMIN | `{name, description?}` (BF22) |
| POST/PUT/DELETE | `/api/claims/admin/categories[/{id}]` | ADMIN | `{name, description?, serviceId}` (BF22) |
| POST  | `/api/claims/admin/services/{id}/agents` | ADMIN | rattacher un agent `{agentId, agentName}` |
| DELETE | `/api/claims/admin/services/{id}/agents/{agentId}` | ADMIN | détacher un agent |

Données initiales : services et catégories **Facturation**, **Support technique**, **Livraison**
(chaque catégorie est traitée par le service du même nom).

### Cycle de vie (diagramme d'états du cahier des charges)
| Transition | Déclencheur | Qui |
|------------|-------------|-----|
| `NEW → CLASSIFIED` | `PUT …/classification` (classement manuel, source `MANUAL`) | ADMIN |
| `NEW → REJECTED` | `PATCH …/status` (demande invalide) | ADMIN |
| `CLASSIFIED → ASSIGNED` | `PUT …/assignment` avec un `agentId` | AGENT, ADMIN |
| `ASSIGNED → IN_PROGRESS` | `PATCH …/status` (prise en charge) | AGENT, ADMIN |
| `IN_PROGRESS → RESOLVED` | `PATCH …/status` | AGENT, ADMIN |
| `RESOLVED → CLOSED` | `PATCH …/status` (validée) | CLIENT propriétaire **uniquement** |
| `RESOLVED → IN_PROGRESS` | `PATCH …/status` (rouverte) | CLIENT propriétaire **uniquement** |

- Toute autre transition → **409**. Transition existante mais rôle non autorisé → **403**.
- Chaque changement de statut est enregistré dans l'historique (ancien, nouveau, auteur, rôle,
  note, date), y compris la création (`null → NEW`).
- **Réaffectation** (`ASSIGNED` ou `IN_PROGRESS`) : le statut est **conservé**, un agent est
  obligatoire, et l'affectation est tracée dans un commentaire interne automatique.
- **Correction de classification** (`CLASSIFIED`, `ASSIGNED`, `IN_PROGRESS`) : le statut est
  conservé, la source passe à `MANUAL`, avec une trace en commentaire interne.
- L'agent désigné doit être rattaché au service choisi (sinon 400).
- Une réclamation `CLOSED` ou `REJECTED` n'accepte plus ni complément, ni commentaire, ni pièce
  jointe (409).

### Règles de visibilité
| Rôle | Voit |
|------|------|
| CLIENT | uniquement ses propres réclamations ; **jamais** les commentaires internes |
| AGENT | les réclamations dont le service fait partie de ses services (rattachement fait par l'admin) **ou** qui lui sont affectées ; donc pas les `NEW` non classées |
| ADMIN | toutes |

Une réclamation hors de portée renvoie **404 partout** (détail, actions, pièces jointes), exactement
comme une réclamation inexistante : son existence n'est jamais révélée. Une action interdite au
rôle sur une réclamation visible renvoie 403.

### Pièces jointes
PDF, PNG, JPG uniquement. Le type est vérifié par la **signature binaire** du fichier, et
l'extension doit correspondre. 5 Mo maximum par fichier (400), 5 fichiers maximum par réclamation
(409). Les fichiers sont stockés dans `CLAIMS_STORAGE_DIR` (hors du dépôt) sous un nom généré
(`<id réclamation>/<uuid>.<ext>`). Le nom d'origine, assaini, ne sert qu'à l'affichage et au
téléchargement (`Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`).

### Événements (préparation de notification-service et stats-service)
`ClaimEventPublisher` est publié **après validation de la transaction** : `CREATED`,
`STATUS_CHANGED`, `CLASSIFICATION_CHANGED`, `ASSIGNED`, `PUBLIC_COMMENT_ADDED`. Pour l'instant,
`LoggingClaimEventPublisher` se contente de journaliser (`Événement … : réclamation …`). Une
implémentation RabbitMQ annotée `@Primary` la remplacera sans modifier `ClaimService`.

### Limites connues
- **Confiance dans la gateway** : claim-service ne revalide pas le JWT ; il croit les en-têtes
  `X-User-*`. Le port **8082 ne doit jamais être exposé** : seule la gateway (8080) doit être
  joignable depuis l'extérieur (réseau, pare-feu). Sinon, n'importe qui pourrait se faire passer
  pour un autre utilisateur en posant ces en-têtes lui-même.
- **Agents saisis par l'admin** : claim-service n'appelle pas auth-service. L'admin saisit l'id du
  compte agent (visible dans `/admin/utilisateurs` ou `GET /api/auth/admin/users`) et son nom.
  Rien ne vérifie que ce compte existe, est actif ou a le rôle AGENT, et un changement de nom
  dans auth-service n'est pas répercuté. Détacher un agent d'un service ne lui retire pas les
  réclamations qui lui sont déjà affectées.
- Sans IA, les nouvelles réclamations attendent le tri manuel de l'admin (`NEW → CLASSIFIED`).

### Script de test complet (`scripts/test-claim-service.ps1`)
Teste le claim-service via la gateway en trois blocs (réclamations et statuts, commentaires et
historique, référentiels et pièces jointes) et affiche le résultat de chaque étape, refus attendus
compris (`-> HTTP 404 : ...`). Identifiants admin lus dans `.env`, jamais affichés, comme les jetons.
Gère les particularités de Windows PowerShell 5.1 (corps UTF-8, multipart sans `-Form`, erreurs
« chunked »). Chaque exécution crée deux clients et un agent `@test.ma` qui restent en base
(à désactiver ensuite avec `PATCH /api/auth/admin/users/{id}/status`).
```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-claim-service.ps1
```

### Tester avec Invoke-RestMethod (PowerShell, depuis la racine du projet)
Scénario complet des critères de réussite, avec de vrais jetons. Les identifiants admin sont lus
dans `.env` sans être affichés. Les comptes de test reçoivent des emails uniques.
```powershell
$G = "http://localhost:8080"; $json = "application/json; charset=utf-8"
# Affiche « code corps » d'une erreur. Sous PowerShell 5.1, le corps JSON (accentué) peut s'afficher
# vide : le code HTTP fait foi ; pour voir le message, refaire l'appel avec curl.exe -s.
function Show-Error { param($e) "{0} {1}" -f [int]$e.Exception.Response.StatusCode, $e.ErrorDetails.Message }
function Api($method, $path, $token, $body) {
  $p = @{ Method = $method; Uri = "$G$path"; Headers = @{ Authorization = "Bearer $token" } }
  if ($body) { $p.ContentType = $json; $p.Body = ($body | ConvertTo-Json) }
  Invoke-RestMethod @p
}
function Login($email, $password) {
  (Invoke-RestMethod -Method Post "$G/api/auth/login" -ContentType $json `
    -Body (@{ email = $email; password = $password } | ConvertTo-Json)).accessToken
}
# Identifiants admin lus dans .env (rien n'est affiché)
$cfg = @{}; Get-Content .env | Where-Object { $_ -match '^\s*([A-Z_]+)=(.*)$' } | ForEach-Object { $cfg[$Matches[1]] = $Matches[2].Trim() }
$A = Login $cfg.ADMIN_EMAIL $cfg.ADMIN_PASSWORD

# Comptes de test : deux clients et un agent
$n = Get-Random; $pw = "motdepasse123"
foreach ($c in "clientA", "clientB") {
  Invoke-RestMethod -Method Post "$G/api/auth/register" -ContentType $json `
    -Body (@{ name = $c; email = "$c-$n@test.local"; password = $pw } | ConvertTo-Json) | Out-Null
}
$agent = Api Post "/api/auth/admin/users" $A @{ name = "Agent Facturation"; email = "agent-$n@test.local"; password = $pw; role = "AGENT" }
$CA = Login "clientA-$n@test.local" $pw; $CB = Login "clientB-$n@test.local" $pw; $AG = Login "agent-$n@test.local" $pw

# L'admin rattache l'agent au service Facturation
$svc = (Api Get "/api/claims/services" $A) | Where-Object name -eq "Facturation"
$cat = (Api Get "/api/claims/categories" $A) | Where-Object name -eq "Facturation"
Api Post "/api/claims/admin/services/$($svc.id)/agents" $A @{ agentId = $agent.id; agentName = $agent.name }

# 1. Le client A crée une réclamation et la voit
$claim = Api Post "/api/claims" $CA @{ title = "Facture en double"; description = "Preleve deux fois en octobre" }
$id = $claim.id; $claim.status                                 # NEW
(Api Get "/api/claims" $CA).content | Select-Object id, title, status
# 2. Le client B ne la voit pas → 404 (même avec un faux X-User-Id, supprimé par la gateway)
try { Api Get "/api/claims/$id" $CB } catch { Show-Error $_ }
try { Invoke-RestMethod "$G/api/claims/$id" -Headers @{ Authorization = "Bearer $CB"; "X-User-Id" = "1"; "X-User-Role" = "ADMIN" } } catch { Show-Error $_ }
# 3. Transition interdite → 409
try { Api Patch "/api/claims/$id/status" $CA @{ status = "CLOSED" } } catch { Show-Error $_ }
# 4. L'admin classe puis affecte au service et à l'agent
(Api Put "/api/claims/$id/classification" $A @{ categoryId = $cat.id }).status          # CLASSIFIED
(Api Put "/api/claims/$id/assignment" $A @{ serviceId = $svc.id; agentId = $agent.id }).status   # ASSIGNED
# 5. L'agent la voit, commente (interne + public), la passe IN_PROGRESS puis RESOLVED
(Api Get "/api/claims" $AG).content | Select-Object id, status
Api Post "/api/claims/$id/comments" $AG @{ content = "Note interne : doublon confirme"; internal = $true }
Api Post "/api/claims/$id/comments" $AG @{ content = "Remboursement lance" }
(Api Patch "/api/claims/$id/status" $AG @{ status = "IN_PROGRESS" }).status
(Api Patch "/api/claims/$id/status" $AG @{ status = "RESOLVED"; note = "Avoir emis" }).status
try { Api Patch "/api/claims/$id/status" $AG @{ status = "CLOSED" } } catch { Show-Error $_ }   # 403 : seul le client clôture
# 6. Le client ne voit pas le commentaire interne, puis clôture
(Api Get "/api/claims/$id" $CA).comments | Select-Object content, internal              # uniquement le public
$closed = Api Patch "/api/claims/$id/status" $CA @{ status = "CLOSED" }
$closed.history | Select-Object oldStatus, newStatus, authorRole, note, changedAt        # NEW → … → CLOSED
# 7. Contrôles de rôle à la gateway : 403
try { Api Get "/api/claims/categories" $CA } catch { Show-Error $_ }
try { Api Post "/api/claims/admin/services" $AG @{ name = "X" } } catch { Show-Error $_ }
```
Pièces jointes (avant la clôture, ou sur une nouvelle réclamation ; PowerShell 5.1 n'a pas
`-Form`, on utilise donc `curl.exe`) :
```powershell
$id2 = (Api Post "/api/claims" $CA @{ title = "Colis abime"; description = "Carton ecrase" }).id
Set-Content -Path "$env:TEMP\test.pdf" -Value "%PDF-1.4`n%%EOF" -NoNewline
curl.exe -s -H "Authorization: Bearer $CA" -F "files=@$env:TEMP\test.pdf;filename=facture.pdf" "$G/api/claims/$id2/attachments"
$att = (Api Get "/api/claims/$id2" $CA).attachments[0].id
curl.exe -s -o "$env:TEMP\copie.pdf" -w "%{http_code}`n" -H "Authorization: Bearer $CA" "$G/api/claims/$id2/attachments/$att"   # 200
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $CB" "$G/api/claims/$id2/attachments/$att"                   # 404
Set-Content -Path "$env:TEMP\faux.pdf" -Value "pas un pdf"
curl.exe -s -H "Authorization: Bearer $CA" -F "files=@$env:TEMP\faux.pdf" "$G/api/claims/$id2/attachments"                     # 400
```

## PostgreSQL (Docker Compose)
Docker Compose sert uniquement aux bases de données (et plus tard à RabbitMQ).
```powershell
docker compose up -d postgres   # démarrer
docker compose ps               # état : "healthy"
docker compose down             # arrêter (données conservées)
docker compose down -v          # arrêter ET supprimer les données (le script d'init sera rejoué)
```
Une base par service, créée au premier démarrage par `docker/postgres/init/01-create-databases.sql`
(actuellement `auth_db` et `claim_db`).

**Volume déjà existant** (le script d'init n'est joué qu'avec un volume vide) : rejouer le script
dans le conteneur. Il est **idempotent** : il ne crée que les bases manquantes et ne touche ni à
`auth_db` ni à ses données.
```powershell
docker exec reclamations-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d postgres -f /docker-entrypoint-initdb.d/01-create-databases.sql'
```
(`$POSTGRES_USER` est lu **dans le conteneur**, grâce aux guillemets simples. Rien à remplacer.)
Vérifier : `docker exec reclamations-postgres sh -c 'psql -U $POSTGRES_USER -d postgres -l'`

Si un PostgreSQL est déjà installé sur la machine, changer `POSTGRES_PORT` dans `.env` (ex. 5433).

## Structure
```
config-repo/          configurations centralisées (servies par config-server)
config-server/        Spring Cloud Config Server — port 8888
discovery-service/    Eureka Server — port 8761
api-gateway/          Spring Cloud Gateway — port 8080
auth-service/         comptes, JWT, rôles — port 8081 (base auth_db)
claim-service/        réclamations, statuts, commentaires, affectations, pièces jointes — port 8082 (base claim_db)
frontend/             React + Vite + Tailwind — port 5173
docker/postgres/init/ création des bases PostgreSQL (une par service)
docs/                 cahier des charges
docker-compose.yml    PostgreSQL
```
