# TODO — Evolucao do Ecossistema de Microservices

## Prioridade 1 — Bug Critico

- [x] **Corrigir formato do evento Kafka**
  - Unificar `PedidoCriadoEvent` entre vendas-service (publica) e transportadora/notificacao (consomem)
  - Implementado com JSON Schema + Confluent Schema Registry
  - Schema canônico: `schemas/pedido-criado-event.json`
  - 25 testes de validacao de schema adicionados nos 3 servicos
  - Arquivos: `schemas/pedido-criado-event.json`, `NOTAS_ALTERACOES.md`

- [ ] **Controle de concorrência no estoque-service**
  - Adicionar `@Lock(LockModeType.PESSIMISTIC_WRITE)` ou `@Version` (optimistic locking) na reserva de estoque
  - Evitar race condition que permite estoque negativo
  - Arquivo afetado: `estoque-service/.../EstoqueService.java`, `EstoqueRepositoryAdapter.java`

---

## Prioridade 2 — Seguranca

- [ ] **Spring Security + OAuth2/JWT no API Gateway**
  - Configurar Keycloak ou Auth0 como Identity Provider
  - Gateway valida token e propaga `subject` para downstreams
  - Adicionar `spring-boot-starter-oauth2-resource-server` no api-gateway
  - Configurar `spring.security.oauth2.resourceserver.jwt.issuer-uri`

- [ ] **Rate limiting no API Gateway**
  - Integrar bucket4j ou Redis-based rate limiting
  - Limitar por IP e/ou por usuario autenticado
  - Configurar limites por rota (ex: /vendas/pedidos = 10 req/s por usuario)

- [ ] **CORS global no API Gateway**
  - Configurar origens permitidas, metodos e headers
  - Evitar que cada servico configure CORS individualmente

- [ ] **Comunicacao service-to-service com mTLS ou service JWT**
  - Evitar que servicos internos se comuniquem sem autenticacao
  - Opcoes: mTLS via service mesh (Istio) ou JWT de servico (client credentials)

---

## Prioridade 3 — Persistencia e Migrations

- [ ] **Flyway ou Liquibase em todos os servicos com banco**
  - Substituir `ddl-auto: update` (perigoso em producao)
  - Servicos afetados: vendas-service, estoque-service
  - Criar scripts de migration versionados

- [ ] **Persistir frete-service e pagamento-service em PostgreSQL**
  - Criar tabelas `frete_calculos` e `pagamentos_transacoes`
  - Evitar perda de dados apos restart (compensacao da saga falha)
  - Criar containers PostgreSQL no docker-compose para cada servico

- [ ] **Adicionar Flyway ao docker-compose**
  - Na inicializacao, executar migrations antes do servico subir

---

## Prioridade 4 — Kafka e Mensageria

- [ ] **Dead Letter Queue (DLQ) no Kafka**
  - Configurar `DefaultErrorHandler` com DLQ topic ao invés de `FixedBackOff(0,0)`
  - Criar topics: `pedido-criado-dlq`, `pedido-criado-dlq-transportadora`, `pedido-criado-dlq-notificacao`
  - Monitorar DLQ via Kafka UI

- [ ] **Schema Registry para contratos de evento**
  - Instalar Confluent Schema Registry no docker-compose
  - Registrar schemas dos eventos (`PedidoCriadoEvent`)
  - Validar compatibilidade antes de publicar/consumir

- [ ] **Idempotencia nos consumers Kafka**
  - transportadora-service e notificacao-service devem verificar se evento ja foi processado
  - Usar `pedidoId` + `sku` como chave de deduplicacao
  - Criar tabela `eventos_processados` ou usar Redis

- [ ] **Outbox pattern no vendas-service**
  - Gravar evento em tabela `outbox` na mesma transacao do pedido
  - Usar Debezium CDC ou polling publisher para enviar ao Kafka
  - Evitar perda de eventos se Kafka cair no meio da saga

---

## Prioridade 5 — Observabilidade

- [ ] **Corrigir Prometheus scrape no Docker Compose**
  - Trocar `host.docker.internal:PORT` por `servico:PORT` (DNS do Docker Compose)
  - Arquivo afetado: `docker-compose.yml` (secao prometheus)

- [ ] **Dashboards Grafana pré-configurados**
  - Dashboard de taxa de erro por servico (5xx rate)
  - Dashboard de latencia p50/p95/p99 por endpoint
  - Dashboard de throughput (requests/s)
  - Dashboard de Circuit Breaker state (CLOSED/OPEN/HALF_OPEN)
  - Dashboard de Kafka consumer lag
  - Dashboard de JVM metrics (heap, GC, threads)

- [ ] **Alertas Prometheus AlertManager**
  - CB OPEN > 5 minutos
  - Taxa de erro > 5% por servico
  - Kafka consumer lag > 1000
  - Disponibilidade < 99.9%
  - Banco de dados offline

- [ ] **Health checks customizados**
  - Adicionar `@HealthIndicator` para: conectividade DB, conectividade Kafka, status de downstreams
  - Configurar `management.endpoint.health.group` no application.yml

- [ ] **Structured logging com MDC**
  - Adicionar `pedidoId`, `sku`, `correlationId` no MDC de todos os servicos
  - Configurar log pattern com campos interpolados

---

## Prioridade 6 — API Gateway

- [ ] **Request/Response size limits**
  - Configurar `spring.codec.max-in-memory-size`

- [ ] **Timeout global**
  - Configurar `spring.cloud.gateway.httpclient.connect-timeout` e `response-timeout`

- [ ] **Retry no gateway para erros 502/503/504**

- [ ] **Circuit breaker no gateway**
  - Adicionar `spring-cloud-starter-circuitbreaker-resilience4j` nas rotas

- [ ] **API versioning via routing**
  - Configurar rotas `/api/v1/**` e `/api/v2/**`

- [ ] **OpenAPI/Swagger aggregation**
  - Agregar specs de todos os servicos via gateway

---

## Prioridade 7 — Saga e Resiliencia

- [ ] **Idempotencia no vendas-service**
  - Verificar se pedido com mesmo `pedidoId` ja existe antes de criar
  - Verificar se reserva com mesmo `pedidoId + sku` ja existe no estoque-service

- [ ] **Bulkhead no Resilience4j**
  - Limitar concorrência de chamadas downstream
  - Ex: maximo 10 chamadas simultaneas para estoque-service
  - Configurar `resilience4j.bulkhead.instances.estoque.max-concurrent-calls`

- [ ] **Time limiter combinado com retry**
  - Usar `Resilience4j` TimeLimiter + CompletableFuture para timeout na chamada Feign
  - Evitar que chamadas travadas bloqueiem o pool de threads

- [ ] **Retry com jitter**
  - Adicionar jitter ao exponential backoff para evitar thundering herd
  - Configurar `resilience4j.retry.instances.estoque.jitter: 0.5`

- [ ] **Avaliar orquestrador de saga (opcional)**
  - Considerar Temporal.io ou Seata para orquestracao centralizada
  - Substituir codigo manual de compensacao por state machine

---

## Prioridade 8 — Padronizacao

- [ ] **Biblioteca compartilhada (shared-lib)**
  - Modulo Maven com: `BusinessException`, `GlobalExceptionHandler`, DTOs de evento, config Resilience4j, config observabilidade
  - Todos os servicos dependem deste modulo

- [ ] **Template de projeto padrao**
  - Criar archetype Maven com estrutura DDD/hexagonal padronizada
  - Estrutura:
    ```
    domain/model/        # Entidades puras
    domain/port/         # Interfaces (hexagonal)
    application/         # Casos de uso
    infrastructure/
      adapter/           # Implementacao das ports
      client/            # Feign clients
      dto/               # DTOs externos
      repository/        # Spring Data
    web/
      controller/        # REST controllers
      dto/               # DTOs de request/response
    ```

- [ ] **Exception handler padronizado**
  - `@RestControllerAdvice` global com formato uniforme:
    ```json
    {"timestamp": "...", "status": 409, "error": "FALHA_ESTOQUE", "message": "...", "path": "/vendas/pedidos"}
    ```

- [ ] **Remover endpoints legados**
  - `VendaController.listarVendas()` — dados mock hardcoded
  - `GET /itens` no estoque-service — duplica `GET /estoque/itens`
  - `GET /status` no pagamento-service — duplica `GET /pagamento/status`

---

## Prioridade 9 — CI/CD e Qualidade

- [x] **Pipeline CI/CD**
  - Build → Unit Tests → Docker Build → Push to GHCR (GitHub Actions)
  - Configurado em `.github/workflows/ci.yml`
  - Matrix paralelo por service, path filters, cache Maven, push para ghcr.io

- [ ] **Quality gates**
  - Cobertura minima 70% (JaCoCo)
  - Zero vulnerabilidades criticas (OWASP dependency check)
  - Zero lint violations (Checkstyle ou Spotless)

- [ ] **Contract testing**
  - Usar Pact ou Spring Cloud Contract
  - Validar compatibilidade entre productores (vendas) e consumidores (transportadora/notificacao)

- [ ] **Mutation testing**
  - Pitest para validar que os testes realmente encontram bugs

---

## Prioridade 10 — Deployment e Operacoes

- [ ] **Healthcheck no Dockerfile de todos os servicos**
  - Adicionar `HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:PORT/actuator/health || exit 1`
  - Apenas discovery-server tem hoje

- [ ] **Resource limits no docker-compose**
  - Configurar `deploy.resources.limits` para memoria e CPU em cada servico

- [ ] **Graceful shutdown**
  - Configurar `server.shutdown: graceful` e `spring.lifecycle.timeout-per-shutdown-phase: 30s`

- [ ] **Feature flags**
  - Usar Spring Cloud Config ou LaunchDarkly para toggle de funcionalidades sem deploy

---

## Testes Faltantes

- [ ] **transportadora-service** — zero testes
- [ ] **notificacao-service** — zero testes
- [ ] **api-gateway** — zero testes
- [ ] **discovery-server** — zero testes

---

## Correcao de Bug Conhecido

- [ ] **PedidoRepositoryAdapter.toDomain()**
  - A condicao `if (entity.getStatus() != StatusPedido.CRIADO)` e muito ampla
  - Para status intermediarios (ESTOQUE_RESERVADO, FRETE_CALCULADO, PAGO) chamaria `marcarFalha()` incorretamente
  - Corrigir para tratar cada status adequadamente
