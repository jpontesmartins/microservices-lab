# Microservices (Eureka + Gateway) - Java 17

Este repositorio contem 5 aplicacoes Spring Boot independentes, para estudo de Service Discovery (Netflix Eureka) e API Gateway (Spring Cloud Gateway).

## Projetos

### `discovery-server`

Funcao: servidor Eureka (service registry).

- Mantem um "catalogo" de instancias registradas (quem esta no ar e onde).
- Os outros projetos usam este servidor para se registrar e para descobrir outros servicos via nome (serviceId).
- Porta (DEV/TST): `8761`
- Console: `http://localhost:8761`

### `api-gateway`

Funcao: ponto de entrada unico (reverse proxy) para os microservicos.

- Recebe chamadas HTTP do cliente e roteia para os servicos usando Discovery + load-balancing (`lb://...`).
- Ajuda a concentrar concerns comuns: roteamento, autenticacao, rate limit, logging, etc (neste projeto estamos focando em roteamento).
- Porta (DEV/TST): `8080`
- Rotas configuradas:
  - `GET http://localhost:8080/vendas` -> `lb://vendas-service/vendas`
  - `POST http://localhost:8080/vendas/pedidos` -> `lb://vendas-service/vendas/pedidos`
  - `GET http://localhost:8080/estoque/itens` -> `lb://estoque-service/estoque/itens`
  - `GET http://localhost:8080/pagamento/status` -> `lb://pagamento-service/pagamento/status`

### `vendas-service`

Funcao: microservico A (exemplo de "vendas") e orquestrador do fluxo de pedido neste lab.

- Fornece um endpoint simples para listar vendas.
- Registra-se no Eureka com o nome `vendas-service` (definido em `spring.application.name`).
- Implementa um fluxo de negocio simples (Processamento de Pedido):
  - Reserva estoque no `estoque-service`.
  - Se a reserva OK, processa pagamento no `pagamento-service`.
  - Se o pagamento falhar, tenta compensar devolvendo o estoque (best-effort).
- Porta (DEV/TST): `8081`
- Endpoint principal:
  - `GET http://localhost:8081/vendas`
  - `POST http://localhost:8081/vendas/pedidos`
  - `GET http://localhost:8081/vendas/pedidos/{pedidoId}`

### `estoque-service`

Funcao: microservico B (exemplo de "estoque").

- Fornece um endpoint simples para listar itens em estoque.
- Registra-se no Eureka com o nome `estoque-service`.
- Porta (DEV/TST): `8082`
- Endpoint principal:
  - `GET http://localhost:8082/estoque/itens`
  - `POST http://localhost:8082/estoque/reservas`
  - `DELETE http://localhost:8082/estoque/reservas/{reservaId}`

### `pagamento-service`

Funcao: microservico C (exemplo de "pagamentos").

- Fornece um endpoint simples de status do servico de pagamentos.
- Registra-se no Eureka com o nome `pagamento-service`.
- Porta (DEV/TST): `8083`
- Endpoint principal:
  - `GET http://localhost:8083/pagamento/status`
  - `POST http://localhost:8083/pagamento/pagamentos`

## Fluxo De Pedido (Inter-service)

O objetivo e simular dependencia real entre servicos usando Service Discovery.

1. Cliente chama o Gateway: `POST /vendas/pedidos`
2. `vendas-service` chama `estoque-service` via Eureka (nome do servico, sem URL hardcoded):
   - `POST http://estoque-service/estoque/reservas`
3. Se o estoque reservar, `vendas-service` chama `pagamento-service` via Eureka:
   - `POST http://pagamento-service/pagamento/pagamentos`
4. Se o pagamento falhar, `vendas-service` tenta compensar:
   - `DELETE http://estoque-service/estoque/reservas/{reservaId}`

### Exemplo de requisicao

`POST http://localhost:8080/vendas/pedidos`

Body:

```json
{ "sku": "ABC-123", "quantidade": 1, "valor": 120.50 }
```

## Circuit Breaker (Resilience4j)

O `vendas-service` usa circuit breaker em chamadas de estoque e pagamento (para estudar falhas, timeouts e recuperacao).

- Configure falhas/latencia no `pagamento-service` via `pagamento.failRate` e `pagamento.delayMs` (application.yml).
- Observe metricas em `GET /actuator/metrics` e `GET /actuator/prometheus` (em cada servico).

## Observabilidade (Actuator)

Os servicos expõem endpoints Actuator basicos para facilitar diagnostico e integracao com o Eureka UI:

- `GET /actuator/health`
- `GET /actuator/info`

No gateway, o Actuator tambem esta presente e os endpoints expostos incluem `health` e `info`.

## Configuracao do Eureka (DEV vs TST)

Todos os clientes Eureka (gateway + microservicos) usam a URL do registry via:

- `EUREKA_URI` (variavel de ambiente), com default para DEV: `http://localhost:8761/eureka`

No `docker-compose.yml`, cada container recebe:

- `EUREKA_URI=http://discovery:8761/eureka`

## Como executar

### DEV (rodar um a um)

1. Suba o `discovery-server`.
2. Suba o `api-gateway` e os microservicos que quiser (em terminais separados).
3. Teste via gateway:
   - `GET http://localhost:8080/vendas`
   - `POST http://localhost:8080/vendas/pedidos`
   - `GET http://localhost:8080/estoque/itens`
   - `GET http://localhost:8080/pagamento/status`

### TST (subir tudo de uma vez)

Na raiz do repositorio:

- `docker compose up --build`

Eureka:

- `http://localhost:8761`

Gateway:

- `http://localhost:8080/vendas`
- `http://localhost:8080/vendas/pedidos`
- `http://localhost:8080/estoque/itens`
- `http://localhost:8080/pagamento/status`
