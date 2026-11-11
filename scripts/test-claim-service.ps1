# Test de bout en bout du claim-service via l'API Gateway (http://localhost:8080).
#
# Prérequis : PostgreSQL, config-server, discovery-service, api-gateway, auth-service et
# claim-service démarrés (voir README). Lancement depuis la racine du dépôt :
#   powershell -ExecutionPolicy Bypass -File scripts\test-claim-service.ps1
#
# Trois blocs : (1) réclamations et statuts, (2) commentaires et historique,
# (3) référentiels et pièces jointes. Chaque ligne affiche le résultat obtenu ; les refus attendus
# s'affichent sous la forme « -> HTTP <code> : <message> ».
#
# Sécurité : ADMIN_EMAIL et ADMIN_PASSWORD sont lus dans .env et jamais affichés ; les jetons restent
# dans des variables, jamais affichés, et sont effacés à la fin. Chaque exécution crée deux clients
# et un agent aux emails uniques (@test.ma) et des réclamations de test : ils restent en base.
#
# Particularités de Windows PowerShell 5.1 prises en compte :
#  - le corps JSON est envoyé en octets UTF-8 (sinon accents mal encodés) ; ces octets sont affectés
#    directement à $p.Body : passés dans un « $x = if (...) { ... } », le pipeline les transformerait
#    en Object[] et Invoke-RestMethod enverrait une suite de nombres (400) ;
#  - les réponses JSON sans charset sont décodées en ISO-8859-1 : Fix() rétablit les accents ;
#  - pour une erreur envoyée en « Transfer-Encoding: chunked », ErrorDetails reste vide : le corps
#    est alors relu dans le flux de la réponse ;
#  - un tableau JSON renvoyé par Invoke-RestMethod n'est pas déroulé dans un pipeline direct :
#    on le range d'abord dans une variable ;
#  - pas de -Form : le corps multipart des pièces jointes est construit à la main.
# Ce fichier est enregistré en UTF-8 avec BOM (nécessaire pour que PowerShell 5.1 lise les accents).

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root '.env'
if (-not (Test-Path $envPath)) { Write-Host "Fichier .env introuvable : $envPath"; exit 1 }

Write-Host "########## BLOC 1 : réclamations et statuts ##########"
$cfg = @{}
Get-Content $envPath | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $cfg[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
}
Write-Host "[0] ADMIN_EMAIL lu : $([bool]$cfg.ADMIN_EMAIL) ; ADMIN_PASSWORD lu : $([bool]$cfg.ADMIN_PASSWORD)"

$base = 'http://localhost:8080'
function Fix([string]$s) {
  if (-not $s -or $s -notmatch '[\u0080-ÿ]') { return $s }
  try { (New-Object Text.UTF8Encoding($false, $true)).GetString([Text.Encoding]::GetEncoding(28591).GetBytes($s)) } catch { $s }
}
function Api {
  param([string]$Method, [string]$Path, [string]$Token, $Body,
        [string]$ContentType = 'application/json; charset=utf-8', [string]$OutFile)
  $p = @{ Method = $Method; Uri = "$base$Path"; Headers = @{} }
  if ($Token) { $p.Headers['Authorization'] = "Bearer $Token" }
  if ($null -ne $Body) {
    $p.ContentType = $ContentType
    if ($Body -is [byte[]]) { $p.Body = $Body } else { $p.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $Body -Depth 5)) }
  }
  if ($OutFile) { $p.OutFile = $OutFile }
  try { Invoke-RestMethod @p }
  catch {
    $e = $_
    if ($e.Exception.Response) {
      $msg = Fix $e.ErrorDetails.Message
      if (-not $msg) {   # réponse « chunked » : PowerShell 5.1 ne remplit pas ErrorDetails
        try { $msg = (New-Object IO.StreamReader($e.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)).ReadToEnd() } catch {}
      }
      try { $msg = (ConvertFrom-Json $msg).message } catch {}
      Write-Host ("    -> HTTP {0} : {1}" -f [int]$e.Exception.Response.StatusCode, $msg)
    } else { Write-Host "    -> Erreur réseau : $($e.Exception.Message)" }
  }
}
function Login($email, $password) { (Api POST '/api/auth/login' -Body @{ email = $email; password = $password }).accessToken }
function Show($c) { if ($c) { Write-Host "    id=$($c.id) statut=$($c.status) categorie=$(Fix $c.category.name) service=$(Fix $c.service.name) agent=$(Fix $c.assignedAgent.name) source=$($c.classificationSource)" } }

# 1. Comptes : admin (depuis .env), deux clients, un agent
$adminToken = Login $cfg.ADMIN_EMAIL $cfg.ADMIN_PASSWORD
Write-Host "[1] Jeton admin obtenu : $([bool]$adminToken)"                          # True
$run = Get-Date -Format 'MMddHHmmss'
$testPwd = 'Test12345!'   # mot de passe des comptes jetables créés par ce script
$c1 = Api POST '/api/auth/register' -Body @{ name = 'Client Un'; email = "client1.$run@test.ma"; password = $testPwd }
$c2 = Api POST '/api/auth/register' -Body @{ name = 'Client Deux'; email = "client2.$run@test.ma"; password = $testPwd }
$agent = Api POST '/api/auth/admin/users' $adminToken @{ name = 'Agent Facturation'; email = "agent.$run@test.ma"; password = $testPwd; role = 'AGENT' }
$c1Token = Login "client1.$run@test.ma" $testPwd
$c2Token = Login "client2.$run@test.ma" $testPwd
$agentToken = Login "agent.$run@test.ma" $testPwd
Write-Host "[1] client1 id=$($c1.id) role=$($c1.role) | client2 id=$($c2.id) role=$($c2.role) | agent id=$($agent.id) role=$($agent.role)"
Write-Host "[1] Jetons : $([bool]$c1Token) $([bool]$c2Token) $([bool]$agentToken)"   # True True True

# 2. Référentiel : rattacher l'agent au service Facturation
$services = Api GET '/api/claims/services' $adminToken
$svc = $services | Where-Object name -eq 'Facturation'
$categories = Api GET '/api/claims/categories' $adminToken
$cat = $categories | Where-Object name -eq 'Facturation'
Write-Host "[2] Service Facturation id=$($svc.id) ; catégorie Facturation id=$($cat.id) (traitée par : $(Fix $cat.service.name))"
$sa = Api POST "/api/claims/admin/services/$($svc.id)/agents" $adminToken @{ agentId = $agent.id; agentName = 'Agent Facturation' }
Write-Host "[2] Rattachement : agentId=$($sa.agentId) agentName=$($sa.agentName) serviceId=$($sa.serviceId)"

# 3. Le client 1 crée la réclamation et la voit (NEW, 1 ligne d'historique)
$claim = Api POST '/api/claims' $c1Token @{ title = 'Facture d''octobre erronée'; description = 'Le montant facturé est le double du montant habituel.' }
Write-Host "[3] Création par client 1 :"; Show $claim
Write-Host "    clientId=$($claim.clientId) (attendu $($c1.id)) ; historique : $($claim.history.Count) ligne(s)"
Write-Host "[3] Client 1 relit la réclamation :"; Show (Api GET "/api/claims/$($claim.id)" $c1Token)
$l1 = Api GET '/api/claims' $c1Token; Write-Host "[3] Liste client 1 : totalElements=$($l1.totalElements) ids=$($l1.content.id -join ',')"

# 4. Le client 2 ne la voit pas (404, jamais 403 ; liste vide)
Write-Host "[4] Client 2 lit la réclamation du client 1 :"; $null = Api GET "/api/claims/$($claim.id)" $c2Token
$l2 = Api GET '/api/claims' $c2Token; Write-Host "[4] Liste client 2 : totalElements=$($l2.totalElements)"

# 5. L'admin classe (CLASSIFIED, MANUAL) puis affecte au service et à l'agent (ASSIGNED)
Write-Host "[5] Admin classe :"; Show (Api PUT "/api/claims/$($claim.id)/classification" $adminToken @{ categoryId = $cat.id })
Write-Host "[5] Admin affecte :"; Show (Api PUT "/api/claims/$($claim.id)/assignment" $adminToken @{ serviceId = $svc.id; agentId = $agent.id })

# 6. Transition interdite (409), traitement par l'agent, clôture refusée à l'agent (403)
Write-Host "[6] Agent tente ASSIGNED -> RESOLVED :"; $null = Api PATCH "/api/claims/$($claim.id)/status" $agentToken @{ status = 'RESOLVED' }
Write-Host "[6] Agent -> IN_PROGRESS :"; Show (Api PATCH "/api/claims/$($claim.id)/status" $agentToken @{ status = 'IN_PROGRESS'; note = 'Prise en charge' })
Write-Host "[6] Agent -> RESOLVED :"; Show (Api PATCH "/api/claims/$($claim.id)/status" $agentToken @{ status = 'RESOLVED'; note = 'Avoir émis sur la prochaine facture' })
Write-Host "[6] Agent tente CLOSED :"; $null = Api PATCH "/api/claims/$($claim.id)/status" $agentToken @{ status = 'CLOSED' }

# 7. Le client clôture ; plus aucune transition possible (409)
Write-Host "[7] Client 1 -> CLOSED :"; Show (Api PATCH "/api/claims/$($claim.id)/status" $c1Token @{ status = 'CLOSED'; note = 'Merci' })
Write-Host "[7] Client 1 tente CLOSED -> IN_PROGRESS :"; $null = Api PATCH "/api/claims/$($claim.id)/status" $c1Token @{ status = 'IN_PROGRESS' }

Write-Host ""
Write-Host "########## BLOC 2 : commentaires et historique ##########"
# 1. Nouvelle réclamation ouverte (la première est clôturée), classée puis affectée
$claim2 = Api POST '/api/claims' $c1Token @{ title = 'Double prélèvement'; description = 'J''ai été prélevé deux fois ce mois-ci.' }
$null = Api PUT "/api/claims/$($claim2.id)/classification" $adminToken @{ categoryId = $cat.id }
Write-Host "[1] Réclamation 2 classée + affectée :"; Show (Api PUT "/api/claims/$($claim2.id)/assignment" $adminToken @{ serviceId = $svc.id; agentId = $agent.id })

# 2. Commentaires publics et interne ; refus pour le client (403) et pour un autre client (404)
$k = Api POST "/api/claims/$($claim2.id)/comments" $agentToken @{ content = 'Nous vérifions vos prélèvements.' }; Write-Host "[2] Commentaire agent public : internal=$($k.internal) authorRole=$($k.authorRole)"
$k = Api POST "/api/claims/$($claim2.id)/comments" $c1Token @{ content = 'Merci, voici la date : le 3 novembre.' }; Write-Host "[2] Commentaire client public : internal=$($k.internal) authorRole=$($k.authorRole)"
$k = Api POST "/api/claims/$($claim2.id)/comments" $agentToken @{ content = 'NOTE INTERNE : erreur du batch de prélèvement.'; internal = $true }; Write-Host "[2] Commentaire agent interne : internal=$($k.internal) authorRole=$($k.authorRole)"
Write-Host "[2] Client 1 tente un commentaire interne :"; $null = Api POST "/api/claims/$($claim2.id)/comments" $c1Token @{ content = 'Je tente un commentaire interne'; internal = $true }
Write-Host "[2] Client 2 commente la réclamation du client 1 :"; $null = Api POST "/api/claims/$($claim2.id)/comments" $c2Token @{ content = 'Intrusion' }

# 3. Vue du client : 2 commentaires publics, aucun interne
$vc = Api GET "/api/claims/$($claim2.id)" $c1Token
Write-Host "[3] Vue client 1 :"
$vc.comments | ForEach-Object { Write-Host "    internal=$($_.internal) role=$($_.authorRole) : $(Fix $_.content)" }
Write-Host "    Commentaires vus par le client : $($vc.comments.Count) ; internes : $(@($vc.comments | Where-Object internal).Count)"
# 4. Vue de l'agent : 4 commentaires (trace d'affectation interne, 2 publics, la note interne)
$va = Api GET "/api/claims/$($claim2.id)" $agentToken
Write-Host "[4] Vue agent :"
$va.comments | ForEach-Object { Write-Host "    internal=$($_.internal) role=$($_.authorRole) : $(Fix $_.content)" }

# 5. Réclamation clôturée : plus de commentaire (409)
Write-Host "[5] Commentaire sur la réclamation clôturée :"; $null = Api POST "/api/claims/$($claim.id)/comments" $c1Token @{ content = 'Encore une question' }

# 6. Historique complet de la réclamation 1 : 6 lignes, de (aucun) -> NEW à RESOLVED -> CLOSED
Write-Host "[6] Historique de la réclamation 1 (vue client) :"
$hist = (Api GET "/api/claims/$($claim.id)" $c1Token).history
$hist | ForEach-Object { Write-Host ("    {0,-11} -> {1,-11} {2,-6} {3}" -f $(if ($_.oldStatus) { $_.oldStatus } else { '(aucun)' }), $_.newStatus, $_.authorRole, (Fix $_.note)) }

Write-Host ""
Write-Host "########## BLOC 3 : référentiels et pièces jointes ##########"
# 1. Référentiels (lecture AGENT/ADMIN) ; un client est refusé par la gateway (403)
Write-Host "[1] Catégories (agent) :"
$list = Api GET '/api/claims/categories' $agentToken
$list | ForEach-Object { Write-Host "    id=$($_.id) $(Fix $_.name) -> service $(Fix $_.service.name)" }
Write-Host "[1] Services (agent) :"
$list = Api GET '/api/claims/services' $agentToken
$list | ForEach-Object { Write-Host "    id=$($_.id) $(Fix $_.name) : $(Fix $_.description)" }
Write-Host "[1] Agents du service Facturation (admin), lignes de ce test :"
$list = Api GET "/api/claims/services/$($svc.id)/agents" $adminToken
Write-Host "    ($(@($list).Count) agent(s) rattaché(s) au total)"
$list | Where-Object agentId -eq $agent.id | ForEach-Object { Write-Host "    agentId=$($_.agentId) agentName=$($_.agentName) serviceId=$($_.serviceId)" }
Write-Host "[1] Client 1 lit les catégories :"; $null = Api GET '/api/claims/categories' $c1Token

# 2. Envoi d'un petit PDF par le client 1 (multipart construit à la main)
$pdfPath = Join-Path $env:TEMP 'test-reclamation.pdf'
[IO.File]::WriteAllText($pdfPath, "%PDF-1.4`n% fichier de test`n%%EOF`n", [Text.Encoding]::ASCII)
$boundary = [guid]::NewGuid().ToString()
$latin1 = [Text.Encoding]::GetEncoding(28591)
$head = "--$boundary`r`nContent-Disposition: form-data; name=`"files`"; filename=`"facture.pdf`"`r`nContent-Type: application/pdf`r`n`r`n"
$multipart = [byte[]]($latin1.GetBytes($head) + [IO.File]::ReadAllBytes($pdfPath) + $latin1.GetBytes("`r`n--$boundary--`r`n"))
$att = @(Api POST "/api/claims/$($claim2.id)/attachments" $c1Token $multipart -ContentType "multipart/form-data; boundary=$boundary")[0]
Write-Host "[2] Upload client 1 : id=$($att.id) fileName=$($att.fileName) contentType=$($att.contentType) size=$($att.size) uploadedById=$($att.uploadedById)"

# 3. Téléchargement par le client 1 et par l'agent : contenu identique (True)
function Same($a, $b) { (Test-Path $a) -and (Test-Path $b) -and ((Get-FileHash $a).Hash -eq (Get-FileHash $b).Hash) }
$dl = Join-Path $env:TEMP 'telecharge-client1.pdf'
Api GET "/api/claims/$($claim2.id)/attachments/$($att.id)" $c1Token -OutFile $dl
Write-Host "[3] Fichier identique (client 1) : $(Same $pdfPath $dl)"
$dlA = Join-Path $env:TEMP 'telecharge-agent.pdf'
Api GET "/api/claims/$($claim2.id)/attachments/$($att.id)" $agentToken -OutFile $dlA
Write-Host "[3] Fichier identique (agent) : $(Same $pdfPath $dlA)"

# 4. Refus pour un autre client : 404 au téléchargement (aucun fichier créé) et à l'envoi
$dl2 = Join-Path $env:TEMP 'telecharge-client2.pdf'; Remove-Item $dl2 -ErrorAction SilentlyContinue
Write-Host "[4] Client 2 télécharge :"; Api GET "/api/claims/$($claim2.id)/attachments/$($att.id)" $c2Token -OutFile $dl2
Write-Host "    Fichier créé pour client 2 : $(Test-Path $dl2)"
Write-Host "[4] Client 2 envoie un fichier :"; $null = Api POST "/api/claims/$($claim2.id)/attachments" $c2Token $multipart -ContentType "multipart/form-data; boundary=$boundary"

# Nettoyage local : fichiers temporaires et variables contenant les identifiants ou les jetons
Remove-Item $pdfPath, $dl, $dlA, $dl2 -ErrorAction SilentlyContinue
Remove-Variable cfg, adminToken, c1Token, c2Token, agentToken -ErrorAction SilentlyContinue
