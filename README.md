# Plateforme intelligente de gestion des réclamations clients

PFA 2026-2027 — architecture microservices (Spring Boot / Spring Cloud) et classification par IA.
Cahier des charges : [`docs/cahier-des-charges.pdf`](docs/cahier-des-charges.pdf).

**État actuel :** `config-server` (8888), `discovery-service` / Eureka (8761), `api-gateway` (8080)
et `auth-service` (8081, PostgreSQL `auth_db`).

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

Générer un `JWT_SECRET` aléatoire (à coller dans `.env`) :
```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```
Format du fichier : `CLE=valeur`, sans guillemets ni espaces autour de `=`, sans `\`.

## Compiler et tester
```powershell
.\mvnw.cmd clean verify
```
Les tests n'ont besoin ni de Docker ni des autres services (H2 en mémoire pour auth-service).

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

Depuis IntelliJ : lancer `ConfigServerApplication`, `DiscoveryServiceApplication`,
`ApiGatewayApplication`, puis `AuthServiceApplication` (répertoire de travail = racine du projet).

## Routes de l'API Gateway (port 8080)
Le frontend n'appelle que la passerelle. Routes définies dans `config-repo/api-gateway.yml` :

| Chemin           | Service (nom Eureka) | État                |
|------------------|----------------------|---------------------|
| `/api/auth/**`   | auth-service         | disponible          |
| `/api/claims/**` | claim-service        | à venir → 503       |
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
| Rôle ADMIN               | `/api/auth/admin/**` (sinon **403**) |
| Prévu (commenté)         | `/api/stats/**` → AGENT ou ADMIN, à activer avec stats-service |
| Jeton valide             | tout le reste, ex. `GET/PUT /api/auth/me`, `/api/claims/**` (sinon **401**) |

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

## PostgreSQL (Docker Compose)
Docker Compose sert uniquement aux bases de données (et plus tard à RabbitMQ).
```powershell
docker compose up -d postgres   # démarrer
docker compose ps               # état : "healthy"
docker compose down             # arrêter (données conservées)
docker compose down -v          # arrêter ET supprimer les données (le script d'init sera rejoué)
```
Une base par service, créée au premier démarrage par `docker/postgres/init/01-create-databases.sql`.
Si le volume existe déjà, créer une nouvelle base à la main, par exemple :
```powershell
docker exec reclamations-postgres psql -U reclamations -d postgres -c "CREATE DATABASE claim_db"
```
Si un PostgreSQL est déjà installé sur la machine, changer `POSTGRES_PORT` dans `.env` (ex. 5433).

## Structure
```
config-repo/          configurations centralisées (servies par config-server)
config-server/        Spring Cloud Config Server — port 8888
discovery-service/    Eureka Server — port 8761
api-gateway/          Spring Cloud Gateway — port 8080
auth-service/         comptes, JWT, rôles — port 8081 (base auth_db)
docker/postgres/init/ création des bases PostgreSQL (une par service)
docs/                 cahier des charges
docker-compose.yml    PostgreSQL
```
