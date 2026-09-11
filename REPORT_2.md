# REPORT_2.md — Análise de Melhores Práticas do Ecossistema Microservices

**Data:** 2026-09-06
**Escopo:** Todos os 8 serviços, infraestrutura, CI/CD, segurança, observabilidade
**Methodology:** Revisão estática de código + análise arquitetural contra padrões de mercado

---

## Índice

1. [Padrões de Comunicação entre Serviços](#1-padrões-de-comunicação-entre-serviços)
2. [Padrões de Mensageria (Kafka)](#2-padrões-de-mensageria-kafka)
3. [Padrões de Resiliência](#3-padrões-de-resiliência)
4. [Padrões de Banco de Dados](#4-padrões-de-banco-de-dados)
5. [Segurança](#5-segurança)
6. [Observabilidade](#6-observabilidade)
7. [Estratégia de Testes](#7-estratégia-de-testes)
8. [Infraestrutura (Docker, CI/CD)](#8-infraestrutura-docker-cicd)
9. [Qualidade de Código](#9-qualidade-de-código)
10. [Design de API](#10-design-de-api)
11. [Resumo Executivo](#11-resumo-executivo)

---

## 1. Padrões de Comunicação entre Serviços

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Service Discovery | ✅ | Eureka com `@FeignClient(name = "estoque-service")` — sem URLs hardcoded |
| Client-side Load Balancing | ✅ | `spring-cloud-starter-loadbalancer` com `lb://` no Feign |
| Comunicação síncrona via Feign | ✅ | Port/Adapter: `IntegracoesPort` desacopla de `IntegracoesService` |
| Comunicação assíncrona via Kafka | ✅ | Event-driven: `PedidoCriadoEvent` com 2 consumers em grupos separados |
| Transactional Outbox | ✅ | Eventos salvos na mesma transação do negócio, publicados via polling |
| API Gateway unificado | ✅ | Spring Cloud Gateway com rotas baseadas em Eureka |

### O que precisa melhorar

**🔴 CRÍTICO — `VendaController` retorna dados mock hardcoded**

`vendas-service/.../VendaController.java:17-23`

```java
@GetMapping("/vendas")
public List<Map<String, Object>> listarVendas() {
    List<Map<String, Object>> vendas = List.of(
            Map.of("id", 1, "cliente", "Alice", "valor", 120.5),
            Map.of("id", 2, "cliente", "Bob", "valor", 300.0));
    return vendas;
}
```

Código morto/placeholder sem valor de negócio. Misleading para qualquer consumidor da API.

**🟠 ALTO — `CompensacaoController` viola arquitetura hexagonal**

`vendas-service/.../CompensacaoController.java:25-26`

Injeta `CompensacaoPendenteJpaRepository` (camada de infraestrutura) diretamente no controller, bypassando o domain port pattern usado no resto do serviço.

**🟠 ALTO — Endpoints duplicados no `EstoqueController`**

`estoque-service/.../EstoqueController.java:34-51`

Dois métodos `listarItens()` com lógica idêntica mapeados para `/itens` e `/estoque/itens`. O primeiro é legado mas continua ativo.

**🟠 ALTO — POST duplicado para DELETE no `FreteController`**

`frete-service/.../FreteController.java:112-121`

`POST /frete/calcular/{freteId}/cancelar` duplica `DELETE /frete/calcular/{freteId}` com lógica idêntica. Viola convenções REST.

**🟡 MÉDIO — Sem versionamento de API**

Nenhum serviço usa prefixo de versão (`/api/v1/...`). Mudanças breaking exigem deploy coordenado.

---

## 2. Padrões de Mensageria (Kafka)

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Schema Registry (Confluent) | ✅ | JSON Schema com `additionalProperties: false` |
| Schema canônico | ✅ | `schemas/pedido-criado-event.json` com validação |
| Transactional Outbox | ✅ | Previne dual-write, index para polling eficiente |
| ErrorHandlingDeserializer | ✅ | Evita poison pills nos consumers |
| Consumer groups separados | ✅ | `transportadora-group` e `notificacao-group` |

### O que precisa melhorar

**🔴 CRÍTICO — Sem Dead Letter Queue (DLQ)**

`transportadora-service/.../KafkaConfig.java:57`

```java
new DefaultErrorHandler(new FixedBackOff(0L, 0L))
```

Mensagens com falha são descartadas silenciosamente. Zero retries, sem DLQ para investigação.

**🟠 ALTO — Classe `PedidoCriadoEvent` duplicada em 3 serviços**

| Serviço | Arquivo |
|---------|---------|
| vendas-service | `infrastructure/dto/PedidoCriadoEvent.java` |
| transportadora-service | `core/dto/PedidoCriadoEvent.java` |
| notificacao-service | `core/dto/PedidoCriadoEvent.java` |

Todos byte-identical. Viola DRY. Qualquer mudança manual precisa ser feita em 3 lugares. Solução: shared library ou code generation a partir do schema.

**🟠 ALTO — `KafkaConfig` duplicado em 2 serviços**

`transportadora-service/.../KafkaConfig.java` e `notificacao-service/.../KafkaConfig.java` são idênticos exceto pelo group ID e package.

**🟡 MÉDIO — `auto.offset.reset: earliest` em produção**

`transportadora-service/.../KafkaConfig.java:43`

No primeiro startup, TODAS as mensagens históricas serão reprocessadas.

**🟡 MÉDIO — Schema não usado para serialização runtime**

O producer configura `KafkaJsonSchemaSerializer` mas não configura `spring.json.skip.type.headers` corretamente.

**🟡 MÉDIO — Sem monitoramento de consumer lag**

Nenhum alerta configurado para lag de consumo no Kafka.

---

## 3. Padrões de Resiliência

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Circuit Breaker (Resilience4j) | ✅ | COUNT_BASED sliding window, slow call detection, HALF_OPEN automático |
| Retry com exponential backoff | ✅ | Multiplier: 2, filtra `BusinessException` (sem retry em erros de negócio) |
| Fallback methods | ✅ | Distingue erros 4xx (business) de 5xx/timeout (transient) |
| Saga pattern com compensação | ✅ | Passos falhos disparam compensação dos passos anteriores |
| Compensação com retry e max attempts | ✅ | Polling configurável, dead-letter após max tentativas |
| Feign timeouts | ✅ | `connectTimeout: 1000`, `readTimeout: 2000` |
| Testes dedicados de resilience | ✅ | 11 classes em `RetryCircuitBreakerCombined/` |

### O que precisa melhorar

**🔴 CRÍTICO — `double` para valores monetários**

`vendas-service/.../Pedido.java:57`, `ItemPedido.java:28`, `PedidoResponse.java:22`

Todos os campos monetários usam `double`. Causa erros de precisão de ponto flutuante. Deveria ser `BigDecimal`.

Impacto no banco: `V1__create_schema_vendas.sql:20-21` usa `double precision` em vez de `NUMERIC(12,2)`.

**🟠 ALTO — `PedidoRepositoryAdapter.toDomain()` mapeia status incorretamente**

`vendas-service/.../PedidoRepositoryAdapter.java:69-71`

```java
if (entity.getStatus() != StatusPedido.CRIADO) {
    pedido.marcarFalha(entity.getStatus(), entity.getMensagemErro());
}
```

Status `ESTOQUE_RESERVADO`, `FRETE_CALCULADO` e `PAGO` são incorretamente mapeados como "falha". O domain model lacks um método de restauração de estado adequado.

**🟡 MÉDIO — `ServiceTokenProvider` retorna `null` em falha**

`vendas-service/.../ServiceTokenProvider.java:77`

Sem fallback ou retry para aquisição de token. Downstream services recebem requests não autenticadas (401).

**🟡 MÉDIO — `CompensacaoController.stats()` carrega todas as entidades em memória**

`vendas-service/.../CompensacaoController.java:72-76`

Três chamadas a `findByStatusOrderByCreatedAtAsc()` materializam listas completas só para chamar `.size()`. Deveria usar queries `COUNT`.

**🟡 MÉDIO — Configuração Resilience4j duplicada entre profiles**

Configuração de retry/circuit breaker repetida quase idêntica em `application.yml` e `src/test/resources/application.yml`.

---

## 4. Padrões de Banco de Dados

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Database per service | ✅ | PostgreSQL dedicado para vendas, estoque, pagamento |
| Flyway migrations | ✅ | Scripts versionados (V1-V4) |
| `ddl-auto: validate` em produção | ✅ | Hibernate valida mas nunca modifica schema |
| Unique constraints para idempotência | ✅ | `UNIQUE(pedido_id, sku)` em reservas, `UNIQUE(pedido_id)` em transações |
| Optimistic concurrency | ✅ | `DataIntegrityViolationException` tratada graceful |
| `REQUIRES_NEW` para compensações | ✅ | Compensações persistem mesmo se transação externa fizer rollback |

### O que precisa melhorar

**🟠 ALTO — Sem volumes persistentes para PostgreSQL em dev**

`docker-compose.yml:278-327`

Dados são perdidos em cada `docker compose down`. Named volumes necessárias para preservar estado entre reinicializações.

**🟡 MÉDIO — Sem paginação em nenhum endpoint de listagem**

`listarItens()`, `listarVendas()`, `listar()` (compensações) — todos retornam result sets ilimitados.

**🟡 MÉDIO — `ItemEstoque.decrementar()` sem validação de negativo**

`estoque-service/.../ItemEstoque.java:27-33`

`decrementar()` e `incrementar()` não verificam se estoque ficaria negativo.

**🟡 MÉDIO — Métodos não utilizados na `CompensacaoRepositoryAdapter`**

`vendas-service/.../CompensacaoRepositoryAdapter.java:39-45`

`marcarEnviado()` e `marcarFalha()` estão vazios (comentário: "not used"). Adapter interface define métodos nunca implementados.

---

## 5. Segurança

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| OAuth2 Resource Server com JWT | ✅ | Keycloak como authorization server |
| Defense in depth | ✅ | JWT validado no gateway E em cada serviço |
| Security Reactive vs Servlet | ✅ | Gateway usa `SecurityWebFilterChain`, services usam `SecurityFilterChain` |
| Role-based access control | ✅ | Claims mapeadas com prefixo `ROLE_` |
| `@EnableMethodSecurity` | ✅ | Autorização em nível de método |
| FeignTokenPropagator | ✅ | Propaga JWT do usuário ou fallback para service token |
| Stateless sessions | ✅ | `SessionCreationPolicy.STATELESS` |
| CSRF desabilitado | ✅ | Apropriado para APIs REST stateless |
| Secrets via .env | ✅ | `.env` gitignored, `.env.example` como template |
| Gitleaks no CI | ✅ | Scan de secrets no pipeline |

### O que precisa melhorar

**🟠 ALTO — Kafka sem autenticação em TODOS os ambientes**

`docker-compose.yml:30`, `docker-compose.prod.yml:37`

```yaml
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
```

Mesmo em produção, Kafka usa plaintext. Sem SSL, sem SASL.

**🟠 ALTO — Schema Registry sem autenticação em produção**

`docker-compose.prod.yml:63`

```yaml
SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8087
```

HTTP plain, sem auth.

**🟡 MÉDIO — `ServiceTokenProvider` sem fallback quando Keycloak está inacessível**

Retorna `null` na falha, sem retry ou circuit breaker na aquisição de token.

**🟡 MÉDIO — Endpoint `/status` em pagamento-service sem autenticação**

`pagamento-service/.../SecurityConfig.java:31`

`.requestMatchers("/status").permitAll()` expõe metadata do serviço sem autenticação.

**🟡 MÉDIO — Sem rate limiting no API Gateway**

README menciona rate limiting como responsabilidade do gateway mas nenhum filtro está configurado.

**🟡 MÉDIO — Defaults fracos no `docker-compose.infra.yml`**

`docker-compose.infra.yml:73-74`: `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}`
Linhas 91, 108, 125: Senhas do Postgres são o próprio nome do banco.

---

## 6. Observabilidade

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Micrometer + Prometheus | ✅ | Metrics em todos os serviços via actuator |
| Distributed tracing (Zipkin) | ✅ | `traceId` e `spanId` no pattern de log |
| 100% trace sampling | ✅ | `probability: 1.0` em produção |
| Feign metrics | ✅ | `feign-micrometer` + `MicrometerCapability` |
| Gateway request logging | ✅ | Method, path, query, routeId, status, duration |
| Structured logging SLF4J | ✅ | Mensagens parametrizadas em todos os serviços |
| Health checks Docker | ✅ | `service_healthy` com `start_period` adequado |
| Observability stack | ✅ | Prometheus + Grafana + Zipkin |

### O que precisa melhorar

**🟠 ALTO — Sem regras de alerting no Prometheus**

`observability/prometheus.yml`

Apenas scrape config, sem `rule_files`, sem alerting rules, sem integração com Alertmanager.

**🟠 ALTO — Zipkin ausente no compose de produção**

`docker-compose.prod.yml` não inclui container Zipkin. Endpoint `ZIPKIN_BASE_URL` não configurado. Dados de tracing vão para lugar nenhum.

**🟡 MÉDIO — Prometheus targets hardcoded para `host.docker.internal`**

`observability/prometheus.yml:9-14`

Funciona apenas no Docker Desktop. Falharia no Linux Docker sem `extra_hosts`. Além disso, `frete-service` (porta 8084) está ausente dos targets.

**🟡 MÉDIO — Sem logging estruturado (JSON)**

Todos os serviços usam logging em texto. Em produção, JSON estruturado (Logstash Logback encoder) habilitaria melhor agregação de logs.

**🟡 MÉDIO — Sem Correlation ID middleware**

Nenhum filtro customizado para propagação de `X-Request-Id` ou `X-Correlation-Id` no gateway.

**🟡 MÉDIO — `circuitbreakerevents` não exposto via actuator**

`include: health,info,metrics,prometheus` — eventos de circuit breaker do Resilience4j não estão acessíveis.

**🔵 BAIXO — Zipkin endpoint usa HTTP em produção**

`ZIPKIN_BASE_URL` não configurado no prod compose, defaults para `http://localhost:9411`.

---

## 7. Estratégia de Testes

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Unit tests extensivos (vendas) | ✅ | 154 testes: service, controller, adapters, resilience |
| Test profiles com H2 | ✅ | Bancos in-memory, dependências externas desabilitadas |
| Integration tests com `@WebMvcTest` | ✅ | Security config testing com `@MockBean JwtDecoder` |
| Concurrency tests | ✅ | `EstoqueConcurrencyTest` para race conditions |
| Schema compatibility tests | ✅ | `PedidoCriadoEventSchemaTest`, `ProducerConsumerCompatibilityTest` |
| CI roda testes automaticamente | ✅ | `mvn -B clean verify` com artifact upload |

### O que precisa melhorar

**🔴 ALTO — Sem testes para api-gateway, frete, transportadora, notificação**

| Serviço | Testes | Status |
|---------|--------|--------|
| api-gateway | 0 | Sem diretório `src/test/java` |
| frete-service | 16 | Apenas unit tests de controller e core |
| transportadora-service | 5 | Apenas schema test |
| notificacao-service | 5 | Apenas schema test |

Nenhum integration test, nenhum controller test para esses serviços.

**🟠 ALTO — Sem contract testing (Pact, Spring Cloud Contract)**

Schema tests validam formato JSON mas não verificam que a interface Feign do serviço A corresponde ao contrato do controller do serviço B. Uma mudança em `EstoqueController.reservar()` quebraria `EstoqueClient` sem detecção.

**🟡 MÉDIO — Testes usam `ddl-auto: create-drop` em vez de Flyway**

`vendas-service/src/test/resources/application.yml:11`

Testes podem passar com schema gerado pelo Hibernate mas falhar com as migrations Flyway reais.

**🟡 MÉDIO — `pagamento-service` sem test configuration**

Nenhum `src/test/resources/application.yml` para pagamento-service.

---

## 8. Infraestrutura (Docker, CI/CD)

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Multi-stage Docker builds | ✅ | Maven build + JRE-only runtime |
| 3 compose environments | ✅ | dev/test, infra (debug), prod |
| Prod: log rotation | ✅ | `json-file` com `max-size`/`max-file` |
| Prod: restart policy | ✅ | `restart: always` |
| Prod: sem ports expostos | ✅ | Só rede interna |
| Prod: Keycloak HTTPS | ✅ | Certs autoassinados |
| CI/CD completo | ✅ | Gitleaks → change detection → matrix build → Docker push |
| Health checks com dependências | ✅ | `service_healthy` conditions |

### O que precisa melhorar

**🔴 ALTO — Containers rodam como root**

Todos os Dockerfiles — estágio de runtime sem `USER` directive.

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Deveria adicionar:
```dockerfile
RUN addgroup --system app && adduser --system --ingroup app app
USER app
```

**🔴 ALTO — Docker layer caching quebrado**

Todos os Dockerfiles copiam `pom.xml` e `src` juntos, invalidando o cache de dependências a cada mudança de código. Padrão correto:

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
```

**🟠 ALTO — Sem `.dockerignore`**

Docker builds copiam diretório `src` inteiro incluindo testes, `.idea`, `target/`.

**🟠 ALTO — `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3` com broker único**

`docker-compose.prod.yml:39`

Replication factor 3 requer pelo menos 3 brokers. Broker único falha ao criar topic `__consumer_offsets`.

**🟠 ALTO — Prod compose sem ingress/load balancer**

`docker-compose.prod.yml` não expõe portas nem inclui nginx/traefik/haproxy. Stack de produção incompleta.

**🟡 MÉDIO — Sem resource limits (CPU/memory) em nenhum container**

Todos os serviços podem consumir recursos ilimitados.

**🟡 MÉDIO — CI não roda integration tests ou Docker smoke tests**

Apenas `mvn clean verify` (unit tests). Sem `docker compose up` + health check no CI.

**🟡 MÉDIO — Missing `service-token` configuration no prod compose para todos os services**

Apenas `vendas-service` tem `SERVICE_TOKEN_CLIENT_SECRET`. Outros services que usam client credentials ficam sem configuração.

---

## 9. Qualidade de Código

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Hexagonal Architecture (vendas) | ✅ | Domain/Port/Adapter com desacoplamento |
| Java Records para DTOs | ✅ | Imutáveis, concisos, auto `equals`/`hashCode` |
| Domain models com encapsulamento | ✅ | Construtores privados, factory methods, coleções imutáveis |
| Idempotency support | ✅ | Header `Idempotency-Key` com validação |
| Javadoc abrangente | ✅ | SecurityConfig, PedidoService, IntegracoesPort |
| Logging consistente | ✅ | Padrão SLF4J parametrizado em todos os serviços |

### O que precisa melhorar

**🔴 CRÍTICO — `double` para valores monetários (já detalhado na Seção 3)**

**🟠 ALTO — SecurityConfig massivamente duplicado em 5 serviços**

| Serviço | Arquivo | Linhas |
|---------|---------|--------|
| vendas-service | `config/SecurityConfig.java` | 95 |
| estoque-service | `config/SecurityConfig.java` | 103 |
| frete-service | `config/SecurityConfig.java` | 92 |
| pagamento-service | `config/SecurityConfig.java` | 58 |

O bean `jwtAuthenticationConverter()` é idêntico nos 4 serviços servlet. Deveria ser extraído para shared library ou auto-configuration.

**🟠 ALTO — Inconsistência no tratamento de exceções**

| Serviço | Padrão |
|---------|--------|
| vendas-service | Custom `BusinessException` e `TransientException` |
| estoque-service | `ResponseStatusException` (Spring built-in) |
| frete-service | `ResponseStatusException` |
| pagamento-service | `ResponseStatusException` |

Nenhum serviço tem `@ControllerAdvice` global.

**🟡 MÉDIO — Simulação de falha hardcoded sem profile**

`frete-service/.../FreteController.java:140-153`, `pagamento-service/.../PagamentoController.java:148-162`

Código de fault injection sempre presente. Deveria ser condicional via profile (ex: `@Profile("chaos")`).

**🟡 MÉDIO — `Map<String, Object>` em vez de DTOs tipados**

`CompensacaoController.java:40-51` usa `Map.of()` para respostas. Perde type safety e auto-documentação.

**🔵 BAIXO — Comentário incerto no pom.xml**

`vendas-service/pom.xml:101-105`: Comentário "quem que usa essa lib?" sobre `spring-boot-starter-aop` (necessário para Resilience4j).

---

## 10. Design de API

### O que está bem

| Prática | Status | Detalhes |
|---------|--------|----------|
| Naming REST consistente | ✅ | Recursos em plural (`/vendas/pedidos`, `/estoque/reservas`) |
| Métodos HTTP corretos | ✅ | POST criação, GET consulta, DELETE cancelamento |
| Respostas de erro estruturadas | ✅ | `status`, `error`, `message` |
| Header `Retry-After` em 503 | ✅ | Para erros transient |
| Idempotency via header | ✅ | `Idempotency-Key` com validação |
| Input validation | ✅ | `PedidoService.validar()` com mensagens descritivas |

### O que precisa melhorar

**🟠 ALTO — POST retorna 200 em vez de 201**

`vendas-service/.../PedidoController.java:42`

```java
return ResponseEntity.ok(response);
```

Deveria ser `ResponseEntity.status(201).body(response)` para criação de recurso.

**🟡 MÉDIO — Sem `@ControllerAdvice` / exception handler global**

Tratamento de exceção feito por controller com try/catch. `@RestControllerAdvice` forneceria formatação consistente e reduziria duplicação.

**🟡 MÉDIO — Formatos de erro inconsistentes entre serviços**

| Serviço | Formato |
|---------|---------|
| vendas-service | `Map.of("status", 409, "error", "Conflict", "message", ...)` |
| estoque-service | `ResponseStatusException` (formato Spring default) |

Nenhum padrão Error envelope (RFC 7807 Problem Details).

**🟡 MÉDIO — Sem paginação, filtering ou sorting**

`GET /vendas`, `GET /estoque/itens`, `GET /vendas/compensacoes` — todos retornam resultados ilimitados.

**🔵 BAIXO — Sem HATEOAS / discoverability**

Nenhum link nas respostas, nenhum formato HAL.

**🔵 BAIXO — `PagamentoController` usa inner records**

`pagamento-service/.../PagamentoController.java:126-138`

`PagamentoRequest` e `PagamentoResponse` como inner records. Inconsistente com vendas-service que usa classes DTO separadas.

---

## 11. Resumo Executivo

### Scores por Categoria

| Categoria | Score | Status |
|-----------|-------|--------|
| Comunicação entre Serviços | 7/10 | 🟡 Bom, com gaps |
| Mensageria (Kafka) | 6/10 | 🟡 Precisa de DLQ e shared library |
| Resiliência | 8/10 | 🟢 Forte, com bug crítico no adapter |
| Banco de Dados | 7/10 | 🟡 Bom, falta paginação e volumes |
| Segurança | 7/10 | 🟡 Bom, Kafka sem auth em prod |
| Observabilidade | 6/10 | 🟡 Bom foundation, sem alerting |
| Testes | 5/10 | 🟠 vendas forte, outros fracos |
| Infraestrutura | 6/10 | 🟡 Bom, Docker root e caching |
| Qualidade de Código | 6/10 | 🟡 Hexagonal bom, duplicação alta |
| Design de API | 6/10 | 🟡 Convenções boas, sem versioning |
| **Geral** | **6.4/10** | **🟠 Funcional, com dívida técnica** |

### Top 5 Ações Prioritárias

| # | Ação | Impacto | Esforço |
|---|------|---------|---------|
| 1 | Trocar `double` por `BigDecimal` em valores monetários | Crítico | Alto |
| 2 | Corrigir `PedidoRepositoryAdapter.toDomain()` — mapeamento de status | Crítico | Baixo |
| 3 | Adicionar DLQ no Kafka + shared library para `PedidoCriadoEvent` | Alto | Médio |
| 4 | Adicionar testes para api-gateway, frete, transportadora, notificação | Alto | Médio |
| 5 | Adicionar auth no Kafka + extrair SecurityConfig para shared library | Alto | Médio |

### Padrões de Mercado Aplicados

| Padrão | Status | Referência |
|--------|--------|------------|
| Database per Service | ✅ | Sam Newman, "Building Microservices" |
| API Gateway | ✅ | Chris Richardson, "Microservices Patterns" |
| Circuit Breaker | ✅ | Michael Nygard, "Release It!" |
| Saga Pattern | ✅ | Chris Richardson |
| Transactional Outbox | ✅ | Martin Kleppmann, "Designing Data-Intensive Applications" |
| Service Discovery | ✅ | Spring Cloud ecosystem |
| CQRS | ❌ | Não aplicado |
| Event Sourcing | ❌ | Não aplicado (Outbox é alternativa mais simples) |
| Strangler Fig | ❌ | Não aplicável (greenfield) |
| Sidecar | ❌ | Não aplicado (observability via library) |
| Health Check | ✅ | 12-Factor App |
| Structured Logging | ❌ | Padrão de texto, não JSON |
| Circuit Breaker Dashboard | ❌ | Não configurado |

---

*Relatório gerado por análise estática do código em 2026-09-06.*
