# Plateforme intelligente de gestion des réclamations clients

PFA 2026-2027 — architecture microservices (Spring Boot / Spring Cloud) et classification par IA.
Cahier des charges : [`docs/cahier-des-charges.pdf`](docs/cahier-des-charges.pdf).

**Étape actuelle : socle + passerelle** — `config-server` (8888), `discovery-service` / Eureka (8761)
et `api-gateway` (8080).

**Mode d'exécution :** l'application s'exécute en local. Docker Compose ne lance que les bases de
données et le broker de messages ; les services tournent directement avec Maven (et plus tard
Python et Node), sans conteneur.

## Prérequis (Windows)
- JDK 17 ou plus récent (testé avec JDK 25), avec `JAVA_HOME` pointant dessus.
- Docker Desktop (uniquement pour PostgreSQL).
- Maven n'est pas nécessaire : le projet fournit le wrapper `mvnw.cmd`.

Vérifier / corriger `JAVA_HOME` dans PowerShell (pour la session en cours) :
```powershell
$env:JAVA_HOME = "C:\Users\stert\.jdks\openjdk-25"
& "$env:JAVA_HOME\bin\java" -version
```
Pour le rendre permanent : *Paramètres système > Variables d'environnement*.

## Compiler et tester
```powershell
.\mvnw.cmd clean verify
```

## Lancer les services (trois terminaux, dans cet ordre)
Les services Java ne sont pas conteneurisés : on les lance avec Maven ou depuis l'IDE.
Attendre que chaque service affiche `Started ...Application` avant de lancer le suivant.

**Terminal 1 — Config Server**
```powershell
.\mvnw.cmd -pl config-server spring-boot:run
```
Vérifier : http://localhost:8888/sample-service/default (configuration d'exemple de `config-repo/`).

**Terminal 2 — Eureka** (une fois le Config Server démarré)
```powershell
.\mvnw.cmd -pl discovery-service spring-boot:run
```
Tableau de bord : http://localhost:8761

**Terminal 3 — API Gateway** (Config Server obligatoire : il fournit les routes)
```powershell
.\mvnw.cmd -pl api-gateway spring-boot:run
```
Vérifier :
- http://localhost:8080/actuator/health → `{"status":"UP"}`
- http://localhost:8761 → `API-GATEWAY` apparaît dans « Instances currently registered » (≈ 30 s)
- http://localhost:8080/api/claims/test → **503 Service Unavailable** (normal : claim-service n'existe pas encore)

Depuis IntelliJ : lancer `ConfigServerApplication`, puis `DiscoveryServiceApplication`, puis `ApiGatewayApplication`.

## Routes de l'API Gateway (port 8080)
Le frontend n'appelle que la passerelle. Les routes sont définies dans `config-repo/api-gateway.yml` :

| Chemin           | Service (nom Eureka) | État                |
|------------------|----------------------|---------------------|
| `/api/auth/**`   | auth-service         | à venir → 503       |
| `/api/claims/**` | claim-service        | à venir → 503       |
| `/api/stats/**`  | stats-service        | à venir → 503       |

CORS autorisé pour le frontend React : http://localhost:3000 et http://localhost:5173.
Chaque requête est tracée dans la console : `GET /api/claims/test -> 503 (56 ms)`.

## PostgreSQL (Docker Compose)
Docker Compose sert uniquement aux bases de données (et plus tard à RabbitMQ).
```powershell
Copy-Item .env.example .env     # puis modifier le mot de passe dans .env
docker compose up -d            # démarre PostgreSQL (port 5432 par défaut)
docker compose ps               # état : "healthy"
docker compose down             # arrêter (données conservées)
docker compose down -v          # arrêter ET supprimer les données
```
Si un PostgreSQL est déjà installé sur la machine, changer `POSTGRES_PORT` dans `.env` (ex. 5433).

## Structure
```
config-repo/        configurations centralisées (servies par config-server)
config-server/      Spring Cloud Config Server — port 8888
discovery-service/  Eureka Server — port 8761
api-gateway/        Spring Cloud Gateway — port 8080
docs/               cahier des charges
docker-compose.yml  PostgreSQL
```
