# ROTEIRO_AMBIENTES.md — Passo a Passo para Subir os Ambientes

---

## Visão Geral

| Ambiente | Compose | O que sobe | Uso |
|----------|---------|-----------|-----|
| **Dev/Debug** | `docker-compose.infra.yml` | Só infraestrutura (Kafka, Keycloak, PostgreSQL) | Debug com IntelliJ, step-by-step |
| **Test** | `docker-compose.yml` | Tudo (infra + apps Spring Boot) | Validação completa, testes de integração |
| **Prod** | `docker-compose.prod.yml` | Tudo com HTTPS, sem ports, logs rotacionados | Simulação de produção |

---

## Pré-requisitos (todos os ambientes)

```powershell
# 1. Docker Desktop rodando
docker --version

# 2. Java 21 instalado
java --version

# 3. Maven instalado
mvn --version

# 4. Na raiz do projeto
cd C:\Users\joao_\dev\codex\microservices
```

---

## Ambiente 1: Dev/Debug

**Quando usar:** Quando quiser debuggar código passo a passo no IntelliJ com breakpoints.

**O que sobe:** Apenas infraestrutura (7 containers). Os services Spring Boot rodam localmente.

### Passo 1 — Subir infraestrutura

```powershell
docker compose -f docker-compose.infra.yml up -d
```

Aguardar todos ficarem healthy:

```powershell
docker compose -f docker-compose.infra.yml ps
```

Saída esperada:

```
NAME              STATUS
zookeeper         Up (healthy)
kafka             Up (healthy)
schema-registry   Up (healthy)
keycloak          Up
postgres-vendas   Up (healthy)
postgres-estoque  Up (healthy)
postgres-pagamento Up (healthy)
zipkin            Up (healthy)
```

### Passo 2 — Configurar IntelliJ

1. Abrir o projeto no IntelliJ
2. **Run → Edit Configurations → + → Spring Boot**
3. Para cada service que quiser rodar, criar uma config:

| Service | Main Class | Profile | Port |
|---------|-----------|---------|------|
| discovery-server | `DiscoveryServerApplication` | (nenhum) | 8761 |
| api-gateway | `ApiGatewayApplication` | `local` | 8080 |
| vendas-service | `VendasServiceApplication` | `local` | 8081 |
| estoque-service | `EstoqueServiceApplication` | `local` | 8082 |
| pagamento-service | `PagamentoServiceApplication` | `local` | 8083 |
| frete-service | `FreteServiceApplication` | `local` | 8084 |
| transportadora-service | `TransportadoraServiceApplication` | `local` | 8085 |
| notificacao-service | `NotificacaoServiceApplication` | `local` | 8086 |

4. Em **VM Options** de cada service (exceto discovery):
   ```
   -Dspring.profiles.active=local
   ```

### Passo 3 — Ordem de inicialização

Rodar os services nesta ordem:

```
1. discovery-server      (primeiro — os outros se registram nele)
2. api-gateway           (segundo — roteamento)
3. vendas-service        (terceiro — orquestrador)
4. estoque-service       (quarto)
5. frete-service         (quinto)
6. pagamento-service     (sexto)
7. transportadora-service (sétimo — Kafka consumer)
8. notificacao-service   (oitavo — Kafka consumer)
```

> **Dica:** Para debug, basta rodar só o `discovery-server` + o service que quer debugar.
> Exemplo: Só `vendas-service` para testar criação de pedido.

### Passo 4 — Testar

```powershell
# Health check do discovery
Invoke-RestMethod http://localhost:8761/actuator/health

# Health check do vendas
Invoke-RestMethod http://localhost:8081/actuator/health

# Listar serviços registrados
Invoke-RestMethod http://localhost:8761/eureka/apps | Select-String -Pattern "<app>"
```

### Passo 4.1 - Subir todos os microsserviços (Com o debug no vendas-service)

```powershell
# 1. discovery-server:
    cd discovery-server
    mvn spring-boot:run

# 2. api-gateway:
	cd api-gateway
	mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=local"

# 3. vendas-service:
	Configurar:
		Edit Run Configurations > Modify Options > Application > VendasServiceApplication > 
        Add VM Options: -Dspring.profiles.active=local
	Aí sim iniciar o Debug.

# 4. estoque-service:
	cd estoque-service
	mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=local"

# 5. frete-service:
	cd frete-service
	mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=local"

# 6. pagamento-service:
	cd pagamento-service
	mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=local"
```


### Passo 5 — Parar

```powershell
# Parar infraestrutura
docker compose -f docker-compose.infra.yml down

# Os services locais param manualmente no IntelliJ
```

### Portas expostas (Dev/Debug)

| Porta | Serviço |
|-------|---------|
| 2181 | Zookeeper |
| 9092 | Kafka |
| 29092 | Kafka (host) |
| 8087 | Schema Registry |
| 8180 | Keycloak |
| 5432 | PostgreSQL Vendas |
| 5433 | PostgreSQL Estoque |
| 5434 | PostgreSQL Pagamento |
| 8761 | Discovery (local) |
| 8080 | API Gateway (local) |
| 8081-8086 | Services (local) |

---

## Ambiente 2: Test

**Quando usar:** Quando quiser rodar a stack completa sem se preocupar com configuração. Tudo sobe com defaults do `.env`.

**O que sobe:** Tudo — 17 containers (infra + apps Spring Boot).

### Passo 1 — Verificar `.env`

O arquivo `.env` já deve existir na raiz (gitignored). Se não existir:

```powershell
Copy-Item .env.example .env
# Editar .env com senhas reais (ou manter defaults para dev)
```

### Passo 2 — Subir tudo

```powershell
docker compose up -d
```

Aguardar todos ficarem prontos:

```powershell
docker compose ps
```

Saída esperada (todos Up):

```
NAME                     STATUS
api-gateway              Up
discovery                Up (healthy)
estoque-service          Up
frete-service            Up
kafka                    Up (healthy)
kafka-ui                 Up
keycloak                 Up
notificacao-service      Up
pagamento-service        Up
postgres-estoque         Up (healthy)
postgres-pagamento       Up (healthy)
postgres-vendas          Up (healthy)
schema-registry          Up (healthy)
transportadora-service   Up
vendas-service           Up
zookeeper                Up (healthy)
```

### Passo 3 — Verificar logs (opcional)

```powershell
# Logs de um serviço específico
docker compose logs vendas-service --tail 50

# Todos os logs (Ctrl+C para sair)
docker compose logs -f

# Só erros
docker compose logs --tail 50 2>&1 | Select-String "ERROR"
```

### Passo 4 — Obter token e testar

```powershell
# 1. Login no Keycloak
$tokenResponse = Invoke-RestMethod `
    -Uri "http://localhost:8180/realms/microservices/protocol/openid-connect/token" `
    -Method POST `
    -Body @{
        grant_type = "password"
        username   = "user1"
        password   = "Password123"
        client_id  = "gateway-public"
    } `
    -ContentType "application/x-www-form-urlencoded"

$token = $tokenResponse.access_token
Write-Host "Token obtido!"

# 2. Criar pedido
$body = @{
    items = @(
        @{ sku = "ABC-123"; quantidade = 2; valor = 25.50 }
    )
    cepDestino = "01310-100"
} | ConvertTo-Json -Depth 3

$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/json"
}

Invoke-RestMethod `
    -Uri "http://localhost:8080/vendas/pedidos" `
    -Method POST `
    -Body $body `
    -Headers $headers | ConvertTo-Json -Depth 5
```

### Passo 5 — URLs de acesso

| URL | O que é |
|-----|---------|
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:8180 | Keycloak Admin Console (admin/admin) |
| http://localhost:8089 | Kafka UI |
| http://localhost:8080 | API Gateway (base URL) |

### Passo 6 — Parar

```powershell
# Parar e remover containers + redes
docker compose down

# Parar e remover TUDO (incluindo volumes de dados)
docker compose down -v
```

---

## Ambiente 3: Prod

**Quando usar:** Simulação de produção com HTTPS, sem ports expostos, logs rotacionados.

**Diferenças do Test:**
- Keycloak em modo `start` (não `start-dev`) com HTTPS
- Ports NÃO expostas na host
- Logs com rotação (json-file max-size/max-file)
- `restart: always`
- Replication factor 3 no Kafka

### Passo 1 — Criar `.env.prod`

```powershell
Copy-Item .env.example .env.prod
```

Editar `.env.prod` com senhas **fortes e únicas**:

```bash
# ============================================
# Keycloak
# ============================================
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=<senha_forte_20plus_chars>
KEYCLOAK_HOSTNAME=localhost
KEYCLOAK_VENDAS_SECRET=<gerar_aleatoriamente>
KEYCLOAK_ESTOQUE_SECRET=<gerar_aleatoriamente>
KEYCLOAK_FRETE_SECRET=<gerar_aleatoriamente>
KEYCLOAK_PAGAMENTO_SECRET=<gerar_aleatoriamente>

# ============================================
# Banco de Dados
# ============================================
POSTGRES_VENDAS_DB=vendas
POSTGRES_VENDAS_USER=vendas
POSTGRES_VENDAS_PASSWORD=<senha_forte>
POSTGRES_ESTOQUE_DB=estoque
POSTGRES_ESTOQUE_USER=estoque
POSTGRES_ESTOQUE_PASSWORD=<senha_forte>
POSTGRES_PAGAMENTO_DB=pagamento
POSTGRES_PAGAMENTO_USER=pagamento
POSTGRES_PAGAMENTO_PASSWORD=<senha_forte>

# ============================================
# Usuários Keycloak
# ============================================
KEYCLOAK_USER1_PASSWORD=<senha_forte>
KEYCLOAK_ADMIN_USER_PWD=<senha_forte>
```

Para gerar senhas aleatórias:

```powershell
# Gerar senha de 32 caracteres
-join ((1..32) | ForEach-Object { [char](Get-Random -Minimum 33 -Maximum 126) })
```

### Passo 2 — Gerar certificados autoassinados

```powershell
# Criar diretório de certs
mkdir -Force certs

# Gerar certificado autoassinado (válido 1 ano)
$cert = New-SelfSignedCertificate `
    -DnsName "localhost" `
    -CertStoreLocation "Cert:\LocalMachine\My" `
    -NotAfter (Get-Date).AddYears(1) `
    -FriendlyName "keycloak-dev-cert"

# Exportar .pfx
Export-PfxCertificate `
    -Cert $cert `
    -FilePath "certs\tls.pfx" `
    -Password (ConvertTo-SecureString -String "change_me" -Force -AsPlainText)

# Converter para .crt + .key (requer openssl)
openssl pkcs12 -in certs/tls.pfx -out certs/tls.crt -nodes -nokeys
openssl pkcs12 -in certs/tls.pfx -out certs/tls.key -nodes -nocerts
```

> **Nota:** Os arquivos `.pfx`, `.crt` e `.key` estão no `.gitignore`.

### Passo 3 — Subir stack de produção

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

Aguardar ficar healthy:

```powershell
docker compose -f docker-compose.prod.yml ps
```

### Passo 4 — Criar usuários no Keycloak

```powershell
# Carregar variáveis do .env.prod
Get-Content .env.prod | ForEach-Object {
    if ($_ -match "^([^#]+)=(.+)$") {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
    }
}

# Rodar script de inicialização
.\keycloak-init\create-users.ps1
```

O script vai:
1. Aguardar Keycloak ficar pronto
2. Fazer login como admin
3. Atualizar secrets dos 4 clients (vendas, estoque, frete, pagamento)
4. Criar/atualizar usuários (user1, admin)

### Passo 5 — Verificar

```powershell
# Logs do Keycloak (verificar se realm foi importado)
docker compose -f docker-compose.prod.yml logs keycloak --tail 20

# Health check (via rede interna)
docker compose -f docker-compose.prod.yml exec discovery curl -s http://localhost:8761/actuator/health
```

> **Nota:** Como as ports não estão expostas na host, os services só são acessíveis de dentro da rede Docker.

### Passo 6 — Parar

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod down

# Ou com volumes
docker compose -f docker-compose.prod.yml --env-file .env.prod down -v
```

---

## Troubleshooting

### Containers não ficam healthy

```powershell
# Ver logs de erro
docker compose logs <nome-do-container> --tail 50

# Reiniciar um container específico
docker compose restart <nome-do-container>
```

### Erro "port already in use"

```powershell
# Verificar quem está usando a porta
netstat -ano | findstr :<porta>

# Matar o processo (substitua <PID>)
taskkill /PID <PID> /F
```

### Keycloak não importa o realm

```powershell
# Verificar se o JSON está no volume certo
docker compose exec keycloak ls /opt/keycloak/data/import/

# Reiniciar Keycloak para forçar import
docker compose restart keycloak
```

### Erro de connection refused nos services locais

Verificar se a infraestrutura está rodando:

```powershell
docker compose -f docker-compose.infra.yml ps
```

### Erro " unauthorized" ao obter token

Verificar se o Keycloak está pronto:

```powershell
Invoke-RestMethod http://localhost:8180/health/ready
```

Se não responder, aguardar 30 segundos e tentar novamente.

### Erro "SKU desconhecido" ao criar pedido

O estoque tem SKUs pré-cadastrados. Use:

```
ABC-123  (Teclado Mecânico)
XYZ-789  (Mouse Gamer)
DEF-456  (Monitor 27pol)
GHI-012  (Webcam Full HD)
JKL-345  (Headset Gamer)
```

---

## Resumo Rápido

```powershell
# DEV/DEBUG (só infra, apps no IntelliJ)
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.infra.yml down

# TEST (tudo, sem worry)
docker compose up -d
docker compose down

# PROD (tudo com HTTPS e .env.prod)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
.\keycloak-init\create-users.ps1
docker compose -f docker-compose.prod.yml --env-file .env.prod down
```
