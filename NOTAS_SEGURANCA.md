# Notas de Seguranca - Implementacao com Keycloak + JWT

## Visao Geral

Este documento documenta a implementacao de seguranca nas APIs do projeto de microservices,
cobrindo autenticacao de usuario (via API Gateway) e comunicacao service-to-service
(vendas-service → estoque/frete/pagamento).

### Arquitetura de Seguranca

```
Browser/App → [api-gateway:8080] → [vendas-service:8081] → [estoque-service:8082]
   JWT              JWT validado          JWT propagado via Feign     JWT validado
   (Authorization   (Reactive Security)   (FeignTokenPropagator)     (Servlet Security)
    Code + PKCE)
```

### Componentes

| Componente | Porta | Papel |
|------------|-------|-------|
| Keycloak | 8180 | Authorization Server (OAuth2/OIDC) |
| API Gateway | 8080 | OAuth2 Resource Server (reactive) |
| vendas-service | 8081 | OAuth2 Resource Server + Feign token propagator |
| estoque-service | 8082 | OAuth2 Resource Server |
| frete-service | 8084 | OAuth2 Resource Server |
| pagamento-service | 8083 | OAuth2 Resource Server |

---

## Fase 1: Keycloak no Docker Compose

**O que foi feito:** Adicionado container Keycloak ao `docker-compose.yml`.

**Justificativa:**
- Keycloak e o padrao de mercado para OAuth2/OIDC em ambientes Spring Boot
- Fornece gerenciamento de usuarios, roles e clients via interface web
- Suporta JWT (JSON Web Tokens) nativamente
- Healthcheck configurado para garantir que os services so iniciam apos Keycloak pronto
- Porta 8180 evita conflito com outras portas ja em uso (8080-8089)
- Modo `start-dev` para desenvolvimento (producao usa HTTPS + banco externo)

---

## Fase 2: Realm e Clients no Keycloak

**O que foi feito:** Criado arquivo `config/keycloak-realm-import.json` com a configuracao completa.

**Justificativa:**
- Realm isolado `microservices` separa este projeto de outros no mesmo Keycloak
- Clients configurados conforme fluxo OAuth2:
  - `gateway-public`: Client type public, Authorization Code + PKCE (para frontend/browser)
  - `vendas-service`: Client type confidential, Client Credentials (service account)
- Roles mapeadas: `USER` (compra), `ADMIN` (gestao), `SERVICE` (comunicacao interna)
- Token custom claim `user_id` permite identificar o usuario no backend
- Validade do token: 5 minutos (acesso) / 30 minutos (refresh) - seguranca adequada

---

## Fase 3: API Gateway Security (Reactive)

**O que foi feito:**
- Adicionadas dependencias `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server` ao `pom.xml`
- Criada classe `SecurityConfig.java` com `SecurityWebFilterChain` (reactive)
- Configurado `jwt.issuer-uri` no `application.yml`

**Justificativa:**
- API Gateway usa Spring Cloud Gateway (WebFlux), portanto requer **Spring Security Reactive**
- `SecurityWebFilterChain` e o bean correto para WebFlux (nao `SecurityFilterChain` que e servlet)
- `ReactiveJwtDecoders.fromIssuerLocation()` valida o JWT automaticamente contra o Keycloak
- Endpoints publicos (`/whoami/**`, `/actuator/**`) nao exigem autenticacao
- Todos os demais endpoints exigem JWT valido
- CSRF desabilitado porque APIs REST sao stateless (tokens bearer)

---

## Fase 4: vendas-service Security (Servlet)

**O que foi feito:**
- Adicionadas dependencias `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server` ao `pom.xml`
- Criada classe `SecurityConfig.java` com `SecurityFilterChain` (servlet)
- Configurado `jwt.issuer-uri` no `application.yml`

**Justificativa:**
- vendas-service usa Spring MVC (servlet), portanto requer **Spring Security Servlet**
- `SecurityFilterChain` e o bean correto para servlet (nao `WebSecurityConfigurerAdapter` que e deprecated)
- Validacao redundante do JWT (gateway ja valida) adiciona seguranca em profundidade
- Se o gateway for comprometido, o vendas-service ainda valida o token
- Endpoints de whoami e actuator sao publicos para healthchecks

---

## Fase 5: Feign Token Propagator

**O que foi feito:**
- Criada classe `FeignTokenPropagator.java` (RequestInterceptor do Feign)
- Registrada como bean no `FeignTracingConfig.java`

**Justificativa:**
- O vendas-service precisa chamar estoque/frete/pagamento com o token do usuario
- `FeignTokenPropagator` extrai o header `Authorization` da request HTTP atual
- Repassa o mesmo `Bearer <token>` para os downstream services via Feign
- Abordagem padrao de mercado para propagacao de identidade em cascata
- Alternativa (client credentials) seria mais complexa e menos transparente
- Compativel com o `FeignTracingConfig` existente (tracing + seguranca no mesmo ponto)

---

## Fase 6: Downstream Services Security (Servlet)

**O que foi feito:**
- Adicionadas dependencias de seguranca aos pom.xml de estoque, frete e pagamento
- Criadas classes `SecurityConfig.java` identicas em cada servico
- Configurado `jwt.issuer-uri` no `application.yml` de cada servico

**Justificativa:**
- Cada servico valida o JWT do usuario independentemente
- Defesa em profundidade: mesmo que o vendas-service seja comprometido, os downstream services validam
- Configuracao identica em todos os services mantem consistencia
- Roles podem ser restringidas por servico (ex: estoque requer ROLE_USER)
- Compativel com o FeignTokenPropagator que propaga o Authorization header

---

## Fase 7: Testes

**Status:** Todos os 110 testes do vendas-service passam (0 falhas, 0 erros).

### Testes Unitarios

#### FeignTokenPropagatorTest (4 testes)
Arquivo: `vendas-service/src/test/java/.../FeignTokenPropagatorTest.java`

| Teste | Descricao |
|-------|-----------|
| `devePropagarTokenQuandoPresente` | Verifica que o header Authorization e adicionado ao request Feign quando existe na request original |
| `devePropagarTokenQuandoHeaderExistente` | Verifica propagacao quando header ja existe no request Feign (preservado) |
| `naoDevePropagarTokenQuandoAusente` | Verifica que nenhum header e adicionado quando nao existe Authorization na request |
| `naoDevePropagarTokenQuandoSemContexto` | Verifica que o propagador funciona sem erro quando nao ha contexto de servlet (fora de request HTTP) |

**Tecnica:** Mocks `RequestContextHolder.getRequestAttributes()` para simular contexto HTTP.

### Testes de Integracao

#### PedidoSecurityIntegrationTest (3 testes)
Arquivo: `vendas-service/src/test/java/.../PedidoSecurityIntegrationTest.java`

| Teste | Descricao |
|-------|-----------|
| `deveRetornar401QuandoNaoTemToken` | GET /vendas/pedidos/{id} sem token retorna 401 Unauthorized |
| `deveRetornar200QuandoUsuarioAutenticadoConsultaPedido` | GET com token mockado e `@WithMockUser(roles="USER")` retorna 200 + dados do pedido |
| `deveRetornar404QuandoPedidoNaoExiste` | GET com token valido para pedido inexistente retorna 404 Not Found |

**Tecnica:** `@WebMvcTest(PedidoController.class)` com `@Import(SecurityConfig.class)` e `@MockBean JwtDecoder`. O JwtDecoder mockado retorna um JWT falso que bypassa a validacao real, permitindo testar o fluxo de autorizacao.

### Ajustes em Testes Existentes
- Todos os 103 testes unitarios pre-existentes continuam passando
- `SecurityConfig` do vendas-service criada com metodos protegidos para facilitar testes

---

## Decisoes Tecnicas

### Por que JWT e nao Session-based?
- APIs REST devem ser stateless
- JWT e o padrao para microservices
- Tokens sao validados localmente (sem consultar Keycloak a cada request)
- Compativel com clients mobile e browser

### Por que propagar o token do usuario e nao client credentials?
- Simplicidade: nao precisa configurar client credentials em cada servico
- Transparencia: os downstream services sabem quem e o usuario real
- Menos configuracao no Keycloak (menos clients confidential)
- Padrao mais comum em arquiteturas de gateway + microservices

### Por que validacao redundante (gateway + services)?
- Seguranca em profundidade (defense in depth)
- Se o gateway for comprometido, os services ainda protegem seus dados
- Permite que cada servico tome decisoes de autorizacao independentemente
- Padrao recomendado pelo OWASP para microservices
