# Diagramas de Sequencia - Fluxo de Pedido

Este documento mostra o fluxo de processamento de pedido no lab (Gateway -> Vendas -> Estoque -> Pagamento).

## Caso 1: Sucesso (Pedido PAGO)

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant G as API Gateway
    participant V as Vendas Service
    participant E as Eureka (Discovery)
    participant S as Estoque Service
    participant P as Pagamento Service

    C->>G: POST /vendas/pedidos\n{sku, quantidade, valor}
    G->>V: POST /vendas/pedidos

    Note over V,E: Resolve "estoque-service" via Eureka\n(Spring Cloud LoadBalancer)
    V->>S: POST /estoque/reservas\n{pedidoId, sku, quantidade}
    S-->>V: 200 ReservaResponse\n{reservaId, status=RESERVADO}

    Note over V,E: Resolve "pagamento-service" via Eureka\n(Spring Cloud LoadBalancer)
    V->>P: POST /pagamento/pagamentos\n{pedidoId, valor}
    P-->>V: 200 PagamentoResponse\n{transacaoId, status=APROVADO}

    V-->>G: 200 PedidoResponse\n{status=PAGO, reservaId, transacaoId}
    G-->>C: 200 PedidoResponse
```

## Caso 2: Erro (Falha no Pagamento + Compensacao)

Neste caso, o `pagamento-service` devolve `503` (ou ocorre timeout). O `vendas-service` aciona o fallback do circuit breaker (retorna `FALHA_TRANSITORIA`) e tenta compensar cancelando a reserva no estoque e o frete (best-effort).

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant G as API Gateway
    participant V as Vendas Service
    participant E as Eureka (Discovery)
    participant S as Estoque Service
    participant P as Pagamento Service

    C->>G: POST /vendas/pedidos\n{sku, quantidade, valor}
    G->>V: POST /vendas/pedidos

    Note over V,E: Resolve "estoque-service" via Eureka\n(Spring Cloud LoadBalancer)
    V->>S: POST /estoque/reservas\n{pedidoId, sku, quantidade}
    S-->>V: 200 ReservaResponse\n{reservaId, status=RESERVADO}

    Note over V,E: Resolve "pagamento-service" via Eureka\n(Spring Cloud LoadBalancer)
    V->>P: POST /pagamento/pagamentos\n{pedidoId, valor}
    P-->>V: 503 Service Unavailable (falha simulada)

    Note over V: Resilience4j Circuit Breaker\naciona fallback (sem exception pro controller)
    V->>S: DELETE /estoque/reservas/{reservaId}\n(compensacao best-effort)
    S-->>V: 204 No Content (ou 404 se ja nao existir)

    V-->>G: 200 PedidoResponse\n{status=FALHA_TRANSITORIA, reservaId}
    G-->>C: 200 PedidoResponse
```

