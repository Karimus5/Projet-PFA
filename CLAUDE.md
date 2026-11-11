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
| api-gateway            | Spring Cloud Gateway (WebFlux) | 8080 | —          | fait (sans JWT) |
| auth-service           | Spring Boot + Security/JWT | 8081  | auth_db        | à faire  |
| claim-service          | Spring Boot + OpenFeign    | 8082  | claim_db       | à faire  |
| notification-service   | Spring Boot                | 8083  | notification_db| à faire  |
| stats-service          | Spring Boot                | 8084  | stats_db       | à faire  |
| ai-classifier-service  | Python / FastAPI / scikit-learn | 8000 | —        | à faire  |
| frontend               | React                      | 5173  | —              | à faire  |

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

  Le chemin est transmis **tel quel** (pas de StripPrefix) : les contrôleurs des services exposent
  donc `/api/auth/...`, `/api/claims/...`, `/api/stats/...`. Le `spring.application.name` de chaque
  service doit correspondre au nom après `lb://`. Service absent d'Eureka → 503 propre.
- CORS **uniquement à la passerelle** (origines http://localhost:3000 et http://localhost:5173) ;
  ne pas configurer de CORS dans les services en aval (DedupeResponseHeader en filet de sécurité).
- `RequestLoggingFilter` (WebFilter) : log `MÉTHODE chemin -> statut (durée)` pour chaque requête.
- Import Config Server **non optionnel** (sans config, pas de routes) → lancer config-server avant.
- Tests : `src/test/resources/application.yml` remplace la config principale (pas de Config Server ni
  d'Eureka), charge `../config-repo/api-gateway.yml`, et simule les instances avec
  `spring.cloud.discovery.client.simple.instances.*` + WireMock.
- JWT (vérification des jetons à la passerelle) : prévu avec auth-service, pas encore fait.

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
- Ordre de démarrage : `docker compose up -d` → config-server → discovery-service → api-gateway →
  autres services.

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
- Code et commentaires en français pour la doc, identifiants en anglais.

## Structure du dépôt
```
pom.xml              POM parent (Spring Boot parent + BOM Spring Cloud, liste des modules)
mvnw, mvnw.cmd, .mvn Maven Wrapper
config-repo/         configurations centralisées servies par config-server
config-server/       Spring Cloud Config Server (profil native)
discovery-service/   serveur Eureka
api-gateway/         Spring Cloud Gateway (réactif) — routes, CORS, journalisation
docs/                cahier des charges et conception
docker-compose.yml   PostgreSQL (puis RabbitMQ) uniquement
.env.example         modèle des variables Docker Compose
```

## Ordre de développement prévu (cahier des charges §12)
1. ✅ Socle : config-server, discovery-service, docker-compose (PostgreSQL).
2. ✅ api-gateway (routes, CORS, logs ; JWT à ajouter) — puis auth-service (JWT, rôles client/agent/admin — BF1–BF5).
3. claim-service (BF6–BF15, BF23), notification-service (BF10), stats-service (BF24–BF26) + RabbitMQ.
4. ai-classifier-service + jeu de données + notebook d'évaluation (BF16–BF20, objectif ≥ 85 % accuracy / F1 macro).
5. Frontend React (interfaces client, agent, admin, tableau de bord).
6. Tests d'intégration, charge, évaluation du modèle.
7. Documentation (OpenAPI/Swagger), rapport, soutenance.

## Environnement de la machine (Windows)
- Les variables JAVA_HOME / MAVEN_HOME de l'utilisateur pointaient vers des dossiers supprimés de
  Downloads, et `java` du PATH est un Java 8. JDK 25 disponible : `C:\Users\stert\.jdks\openjdk-25`.
- Toujours utiliser `mvnw.cmd` (pas besoin de Maven dans le PATH) avec un JAVA_HOME valide.
