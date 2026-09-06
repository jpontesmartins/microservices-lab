# keycloak-init/create-users.ps1
# Atualiza secrets dos clients e cria usuarios no Keycloak via API REST
# Usado em PRODUCAO (em dev, o realm JSON ja cria tudo)

param(
    [string]$KeycloakUrl = "http://localhost:8180",
    [string]$AdminUser = $env:KEYCLOAK_ADMIN_USER,
    [string]$AdminPass = $env:KEYCLOAK_ADMIN_PASSWORD
)

Write-Host "=== Keycloak Init - Atualizando secrets e criando usuarios ==="

# Aguardar Keycloak
Write-Host "Aguardando Keycloak ficar pronto..."
$maxAttempts = 30
$attempt = 0
do {
    Start-Sleep -Seconds 3
    $attempt++
    try {
        $null = Invoke-RestMethod "$KeycloakUrl/health/ready" -ErrorAction Stop
        Write-Host "Keycloak pronto!"
        break
    } catch {
        if ($attempt -ge $maxAttempts) {
            Write-Host "Timeout aguardando Keycloak"
            exit 1
        }
        Write-Host "  tentativa $attempt/$maxAttempts..."
    }
} while ($true)

# Login
Write-Host "Fazendo login como admin..."
$tokenResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
    -Method POST -Body @{
    grant_type = "password"
    username   = $AdminUser
    password   = $AdminPass
    client_id  = "admin-cli"
}
$token = $tokenResponse.access_token
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

# Atualizar secrets dos clients
$clients = [ordered]@{
    "vendas-service"    = $env:KEYCLOAK_VENDAS_SECRET
    "estoque-service"   = $env:KEYCLOAK_ESTOQUE_SECRET
    "frete-service"     = $env:KEYCLOAK_FRETE_SECRET
    "pagamento-service" = $env:KEYCLOAK_PAGAMENTO_SECRET
}

foreach ($clientId in $clients.Keys) {
    $secret = $clients[$clientId]
    Write-Host "Atualizando secret do client: $clientId"
    
    $client = Invoke-RestMethod `
        -Uri "$KeycloakUrl/admin/realms/microservices/clients?clientId=$clientId" `
        -Headers $headers
    
    if ($client.Count -gt 0) {
        $clientUuid = $client[0].id
        $body = @{ secret = $secret } | ConvertTo-Json
        Invoke-RestMethod `
            -Uri "$KeycloakUrl/admin/realms/microservices/clients/$clientUuid" `
            -Method PUT -Headers $headers -Body $body
        Write-Host "  OK"
    } else {
        Write-Host "  Client nao encontrado: $clientId"
    }
}

# Criar/atualizar usuario user1
Write-Host "Criando/atualizando usuario: user1"
$user1Body = @{
    username  = "user1"
    enabled   = $true
    email     = "user1@microservices.com"
    firstName = "Usuario"
    lastName  = "Teste 1"
} | ConvertTo-Json

try {
    Invoke-RestMethod `
        -Uri "$KeycloakUrl/admin/realms/microservices/users" `
        -Method POST -Headers $headers -Body $user1Body
    Write-Host "  Usuario criado"
} catch {
    Write-Host "  Usuario ja existe"
}

$user1Pass = $env:KEYCLOAK_USER1_PASSWORD
$passBody = @{ type = "password"; value = $user1Pass; temporary = $false } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "$KeycloakUrl/admin/realms/microservices/users/user1/reset-password" `
    -Method PUT -Headers $headers -Body $passBody
Write-Host "  Senha atualizada"

# Criar/atualizar usuario admin
Write-Host "Criando/atualizando usuario: admin"
$adminBody = @{
    username  = "admin"
    enabled   = $true
    email     = "admin@microservices.com"
    firstName = "Admin"
    lastName  = "Sistema"
} | ConvertTo-Json

try {
    Invoke-RestMethod `
        -Uri "$KeycloakUrl/admin/realms/microservices/users" `
        -Method POST -Headers $headers -Body $adminBody
    Write-Host "  Usuario criado"
} catch {
    Write-Host "  Usuario ja existe"
}

$adminPass = $env:KEYCLOAK_ADMIN_USER_PWD
$passBody = @{ type = "password"; value = $adminPass; temporary = $false } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "$KeycloakUrl/admin/realms/microservices/users/admin/reset-password" `
    -Method PUT -Headers $headers -Body $passBody
Write-Host "  Senha atualizada"

Write-Host ""
Write-Host "=== Setup concluido com sucesso! ==="
