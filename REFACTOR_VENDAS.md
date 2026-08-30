# Refatorações - vendas-service

## DDD / Clean Architecture

Estrutura de pastas reorganizada por bounded context `pedido`, seguindo Domain-Driven Design e Clean Architecture:

```
pedido/
├── domain/
│   ├── model/
│   │   ├── Pedido.java
│   │   ├── ItemPedido.java
│   │   └── StatusPedido.java
│   └── port/
│       ├── IntegracoesPort.java
│       ├── PedidoRepositoryPort.java
│       └── EventoPublicacaoPort.java
├── application/
│   └── PedidoService.java
├── entities/
│   ├── PedidoEntity.java
│   └── PedidoItemEntity.java
├── infrastructure/
│   ├── adapter/
│   │   ├── PedidoRepositoryAdapter.java
│   │   ├── EventoKafkaAdapter.java
│   │   └── IntegracoesService.java
│   ├── client/
│   │   ├── EstoqueClient.java
│   │   ├── FreteClient.java
│   │   └── PagamentoClient.java
│   ├── dto/
│   │   ├── EstoqueDTO.java
│   │   ├── FreteDTO.java
│   │   ├── PagamentoDTO.java
│   │   └── PedidoCriadoEvent.java
│   └── repository/
│       └── PedidoJpaRepository.java
└── web/
    ├── controller/
    │   └── PedidoController.java
    └── dto/
        ├── CriarPedidoRequest.java
        ├── PedidoResponse.java
        ├── ItemPedidoRequest.java
        └── ItemPedidoResponse.java
shared/
└── exception/
    └── BusinessException.java
```

### Camadas e responsabilidades

- **domain/model**: Entidades de negócio puras, sem dependência de framework. `Pedido` encapsula regras de estado (saga) e `ItemPedido` encapsula dados de cada produto.
- **domain/port**: Interfaces que definem contratos de saída (ports). A camada de aplicação depende apenas dessas interfaces.
- **application**: Casos de uso. `PedidoService` orquestra o fluxo saga (reserva → frete → pagamento) com transações compensatórias.
- **entities**: JPA entities para persistência. Mapeamento domain ↔ entity feito no adapter.
- **infrastructure/adapter**: Implementação dos ports. `PedidoRepositoryAdapter` converte entre domain model e JPA entities. `IntegracoesService` implementa `IntegracoesPort` com chamadas HTTP via Feign + Circuit Breaker.
- **web/controller**: Endpoints REST. Depende apenas do `PedidoService`.
- **web/dto**: Records para request/response da API.
- **shared/exception**: Exceções de negócio compartilhadas entre bounded contexts.

### Mudanças específicas

- `PedidoCore` renomeado para `PedidoService` (application layer use case)
- `BusinessException` movida de `integration/` para `shared/exception/`
- `IntegracoesService` implementa `IntegracoesPort` (domain port) com `@CircuitBreaker`; métodos fallback retornam tipos do port (`ReservaEstoqueResult`, `FreteResult`, `PagamentoResult`)

---

## ItemPedido — Itens do Pedido

Cada pedido agora contém uma lista de itens (`List<ItemPedido>`) em vez de um único produto. Cada item possui suas próprias integrações de estoque e frete.

### Modelo de domínio

- **ItemPedido**: sku, quantidade, valorUnitario, reservaId, freteId, valorFrete, prazoEntrega. Factory method `criar()` e `getSubtotal()`.
- **Pedido**: mantém `cepDestino`, `status`, `transacaoId`, `criadoEm` + `List<ItemPedido> items`. Métodos `calcularValorTotal()` e `calcularValorFreteTotal()` somam valores dos itens.

### Saga com multiplos itens

O fluxo saga itera sobre cada item:
1. **Reserva de estoque** — para cada item: chama `reservarEstoque(pedidoId, sku, quantidade)` → item recebe `reservaId`
2. **Cálculo de frete** — para cada item: chama `calcularFrete(pedidoId, sku, quantidade, cepDestino)` → item recebe `freteId`, `valorFrete`, `prazoEntrega`
3. **Pagamento** — uma única chamada com `valorTotal` (soma de subtotais + fretes)

Compensação best-effort itera sobre os itens: cancela reserva de estoque e frete de cada item que já foi processado.

### Persistência

- **pedidos** — tabela principal com `pedido_id`, `cep_destino`, `status`, `criado_em`, `transacao_id`
- **pedido_itens** — tabela de itens com FK para `pedido_id`, campos `sku`, `quantidade`, `valor_unitario`, `reserva_id`, `frete_id`, `valor_frete`, `prazo_entrega`
- `PedidoEntity` usa `@OneToMany(mappedBy = "pedido", cascade = ALL, orphanRemoval = true)`
- `PedidoItemEntity` usa `@ManyToOne(fetch = LAZY)` com `@JoinColumn`

### API

Request:
```json
{
  "items": [
    { "sku": "ABC-123", "quantidade": 2, "valor": 199.90 },
    { "sku": "DEF-456", "quantidade": 1, "valor": 50.00 }
  ],
  "cepDestino": "01310-100"
}
```

Response:
```json
{
  "pedidoId": "...",
  "status": "PAGO",
  "items": [
    {
      "sku": "ABC-123", "quantidade": 2, "valorUnitario": 199.90,
      "subtotal": 399.80, "valorFrete": 20.0, "prazoEntrega": "3 dias uteis",
      "reservaId": "reserva-001", "freteId": "frete-001"
    },
    {
      "sku": "DEF-456", "quantidade": 1, "valorUnitario": 50.00,
      "subtotal": 50.00, "valorFrete": 10.0, "prazoEntrega": "2 dias uteis",
      "reservaId": "reserva-002", "freteId": "frete-002"
    }
  ],
  "valorTotal": 479.80,
  "valorFreteTotal": 30.0,
  "transacaoId": "transacao-001",
  "criadoEm": "2026-08-29T16:19:04.743361Z"
}
```

### Evento Kafka (PedidoCriadoEvent)

```java
record PedidoCriadoEvent(String pedidoId, List<ItemEvent> items, double valorTotal, double valorFreteTotal, String cepDestino) {
    record ItemEvent(String sku, int quantidade, double valorUnitario, double subtotal) {}
}
```

### Validação

A validação em `PedidoService.validar()` checa:
- `items` não pode ser null ou vazio
- `cepDestino` obrigatório
- Cada item: `sku` obrigatório, `quantidade > 0`, `valor > 0`
- Erros são indexados: `item[0].sku obrigatorio`, `item[1].quantidade deve ser > 0`

---

## JPA + PostgreSQL 14

### Dependências (pom.xml)

- `spring-boot-starter-data-jpa`
- `postgresql` (runtime)
- `h2` (test)

### Configuração

- **Produção** (`application.yml`): datasource PostgreSQL com `ddl-auto: update`
- **Testes** (`application-test.yml` / test profile): H2 in-memory com `ddl-auto: create-drop`

### Docker Compose

PostgreSQL 14 adicionado com healthcheck:
```yaml
postgres:
  image: postgres:14
  environment:
    POSTGRES_DB: vendas
    POSTGRES_USER: vendas
    POSTGRES_PASSWORD: vendas
  ports: ["5432:5432"]
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U vendas"]
    interval: 5s
    retries: 5
```

`vendas-service` depende do `postgres` com variáveis de ambiente para datasource.

---

## Circuit Breaker (Resilience4j)

`IntegracoesService` implementa `IntegracoesPort` com anotações `@CircuitBreaker` nos métodos de integração. Fallback methods retornam tipos do port (não DTOs antigos).

Exceções de negócio (`BusinessException`) são propagadas diretamente — o Circuit Breaker só aciona para falhas transitórias (timeout, connection refused).

---

## Testes

- **60 testes** (unit + integration)
- `PedidoServiceTest` — mocks `IntegracoesPort`, `PedidoRepositoryPort`, `EventoPublicacaoPort`. Testa fluxo completo, falha por item, compensação, multiplos itens, validação.
- `CircuitBreakerIntegrationTest` — `@SpringBootTest` com H2 in-memory. Testa fallback, ciclo de vida, métricas, recuperação.
- `CircuitBreakerIsolatedTest` — testes isolados de fallback, métricas, recovery e ciclo de vida.
- `IntegracoesServiceTest` — testes de cada operação (reserva, frete, pagamento, compensações) com cenários de sucesso, erro de negócio e erro de servidor.

---

## Postman Collection

`postman_collection.json` atualizado com o novo formato de request/response:
- Todos os POSTs de criação de pedido usam `{ items: [{sku, quantidade, valor}], cepDestino }`
- Script de extração de variáveis lê `reservaId` e `freteId` de `items[0]`
