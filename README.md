# Microservices (Eureka + Gateway) - Java 21

Este repositorio contem 6 aplicacoes Spring Boot independentes, para estudo de Service Discovery (Netflix Eureka) e API Gateway (Spring Cloud Gateway).

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
  - `POST http://localhost:8080/frete/calcular` -> `lb://frete-service/frete/calcular`

### `vendas-service`

Funcao: microservico A (exemplo de "vendas") e orquestrador do fluxo de pedido neste lab.

- Fornece um endpoint simples para listar vendas.
- Registra-se no Eureka com o nome `vendas-service` (definido em `spring.application.name`).
- Implementa um fluxo de negocio simples (Processamento de Pedido):
  - Reserva estoque no `estoque-service`.
  - Calcula frete no `frete-service`.
  - Se estoque e frete OK, processa pagamento no `pagamento-service` (valor = produto + frete).
  - Se o pagamento falhar, tenta compensar devolvendo o estoque e cancelando o frete (best-effort).
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

### `frete-service`

Funcao: microservico D (exemplo de "frete/envio").

- Calcula o valor e prazo de entrega baseado no CEP de destino.
- Registra-se no Eureka com o nome `frete-service`.
- Porta (DEV/TST): `8084`
- Endpoint principal:
  - `POST http://localhost:8084/frete/calcular`
  - `DELETE http://localhost:8084/frete/calcular/{freteId}`
- Simula falhas e latencia via `frete.failRate` e `frete.delayMs` (application.yml).

## Fluxo De Pedido (Inter-service)

O objetivo e simular dependencia real entre servicos usando Service Discovery.

1. Cliente chama o Gateway: `POST /vendas/pedidos`
2. `vendas-service` chama `estoque-service` via Eureka (nome do servico, sem URL hardcoded):
   - `POST http://estoque-service/estoque/reservas`
3. Se o estoque reservar, `vendas-service` chama `frete-service` via Eureka:
   - `POST http://frete-service/frete/calcular`
4. Se o frete calcular, `vendas-service` chama `pagamento-service` via Eureka:
   - `POST http://pagamento-service/pagamento/pagamentos`
5. Se o pagamento falhar, `vendas-service` tenta compensar:
   - `DELETE http://estoque-service/estoque/reservas/{reservaId}`
   - `DELETE http://frete-service/frete/calcular/{freteId}`

### Exemplo de requisicao

`POST http://localhost:8080/vendas/pedidos`

Body:

```json
{ "sku": "ABC-123", "quantidade": 1, "valor": 120.50, "cepDestino": "01310-100" }
```

## Circuit Breaker (Resilience4j)

O `vendas-service` usa Resilience4j Circuit Breaker em chamadas de estoque, frete e pagamento, protegendo contra falhas em cascata e latencia elevada.

### Configuracao das Instancias

Cada circuit breaker (`estoque`, `frete` e `pagamento`) e configurado via `application.yml`:

| Parametro | Valor | Explicacao |
|-----------|-------|------------|
| `slidingWindowType` | COUNT_BASED | Janela deslizante contabiliza **quantidade** de chamadas (TIME_BASED contabiliza tempo). |
| `slidingWindowSize` | 10 | Quantidade de chamadas na janela para calcular a taxa de falha. |
| `minimumNumberOfCalls` | 5 | Minimo de chamadas antes de avaliar a taxa (evita abrir com poucos dados). |
| `failureRateThreshold` | 50 | Percentual de falhas que dispara a transicao para OPEN. |
| `slowCallRateThreshold` | 60 | Percentual de chamadas lentas que tambem dispara a transicao para OPEN. |
| `slowCallDurationThreshold` | 2s | Chamadas com duracao superior sao consideradas lentas (slow calls). |
| `waitDurationInOpenState` | 10s | Tempo que o circuit breaker fica OPEN antes de tentar HALF_OPEN. |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Chamadas permitidas no estado HALF_OPEN para testar se o servico voltou. |
| `automaticTransitionFromOpenToHalfOpenEnabled` | true | Transicao automatica de OPEN para HALF_OPEN apos waitDuration (sem precisar de chamada). |
| `recordExceptions` | IOException, TimeoutException, HttpServerErrorException, ResourceAccessException | Excecoes registradas como falhas no calculo da taxa. |
| `ignoreExceptions` | BusinessException | Erros de negocio (4xx do downstream) que **nao** contam como falhas e **nao** acionam o fallback. |

### Estados do Circuit Breaker

```
        falhas >= threshold
  CLOSED ──────────────────► OPEN
    ▲                            │
    │                            │ waitDuration
    │                            ▼
    └── chamadas OK ────── HALF_OPEN
         >= threshold
```

- **CLOSED**: Chamadas passam normalmente. Taxa de falha e monitorada.
- **OPEN**: Chamadas sao rejeitadas imediatamente (fallback acionado).
- **HALF_OPEN**: Chamadas de teste sao permitidas. Se OK, volta pra CLOSED; senao, volta pra OPEN.

### Distinguindo Erro de Negocio de Erro de Servidor

Os fallbacks distinguem dois tipos de falha:

- **Erro de servidor** (5xx, timeout, conexao, CircuitBreaker OPEN): o fallback retorna `FALHA_TRANSITORIA`. O `PedidoCore` seta `FALHA_TRANSITORIA` no pedido e aciona compensacao.
- **Erro de negocio** (4xx do downstream, ex: "sem estoque"): o fallback lança `BusinessException` (configurada em `ignoreExceptions`). O `PedidoCore` captura e seta `FALHA_ESTOQUE`, `FALHA_FRETE` ou `FALHA_PAGAMENTO` conforme o servico.

| Metodo | Erro de Servidor (fallback) | Erro de Negocio (BusinessException) |
|--------|----------------------------|--------------------------------------|
| `reservarEstoque()` | `FALHA_TRANSITORIA` | `FALHA_ESTOQUE` |
| `calcularFrete()` | `FALHA_TRANSITORIA` | `FALHA_FRETE` |
| `processarPagamento()` | `FALHA_TRANSITORIA` | `FALHA_PAGAMENTO` |

### Como Testar

1. Configure falhas no `frete-service` ou `pagamento-service` via `application.yml`:
   - `frete.failRate=0.8` (80% de falhas simuladas no frete)
   - `frete.delayMs=3000` (latencia simulada de 3s no frete)
   - `pagamento.failRate=0.8` (80% de falhas simuladas no pagamento)
   - `pagamento.delayMs=3000` (latencia simulada de 3s no pagamento)

2. Apos 5 chamadas (minimumNumberOfCalls), o circuit breaker correspondente deve abrir.

3. Chamadas subsequentes acionam o fallback sem chamar o servico remoto.

4. Apos 10s (waitDuration), o circuit breaker entra em HALF_OPEN e testa novamente.

- [X] Observe metricas em `GET /actuator/metrics` e `GET /actuator/prometheus` (em cada servico).

## Observabilidade (Actuator)

Os servicos expõem endpoints Actuator basicos para facilitar diagnostico e integracao com o Eureka UI:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/prometheus`

No gateway, o Actuator tambem esta presente e os endpoints expostos incluem `health` e `info`.

## Logs E Tracing

Os servicos agora registram logs por etapa do fluxo:

- entrada no gateway, rota escolhida e tempo total da chamada;
- validacao e criacao do pedido no `vendas-service`;
- reserva e cancelamento de estoque no `estoque-service`;
- calculo e cancelamento de frete no `frete-service`;
- simulacao, aprovacao e falhas do pagamento no `pagamento-service`.

Os logs usam `traceId` e `spanId` no pattern, entao quando o Zipkin estiver ativo fica mais facil correlacionar uma mesma requisicao entre servicos.

Se o Zipkin estiver rodando fora do host da aplicacao, ajuste `ZIPKIN_BASE_URL`. No `docker-compose.yml` da raiz, ele ja aponta para `http://host.docker.internal:9411` para conversar com o compose de observabilidade.

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
   - `POST http://localhost:8080/frete/calcular`

### DEV (multiplas instancias: Load Balancer)

> ❗ Não consegui ainda configurar o PushGateway. Sem pushgateway, sem envio das métricas das instâncias que não tem porta fixa.
> Os únicos serviços com porta fixa são o `api-gateway` e o `vendas-service`.
> Ambos estão sendo monitorados.

Para simular mais de 1 instancia localmente (sem conflito de porta), use o profile `dev` no `estoque-service`, `pagamento-service` e `frete-service` (ele usa `server.port=0`).

Em terminais diferentes, no mesmo servico:

- `pagamento-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`
- `estoque-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`
- `frete-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`

Comando para rodar com o profile `dev`:  
`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`

Para visualziar o balanceamento de carga, chame repetidamente:

- `http://localhost:8080/whoami/pagamento`
- `http://localhost:8080/whoami/estoque`

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
- `http://localhost:8080/frete/calcular`

## Load Balancing (Spring Cloud LoadBalancer)

Este ecosistema usa Spring Cloud LoadBalancer para distribuir chamadas quando houver mais de uma instancia do mesmo servico registrada no Eureka:

- Gateway: rotas `lb://...` passam pelo LoadBalancer.
- Vendas: chamadas Feign para `estoque-service`, `frete-service` e `pagamento-service` passam pelo LoadBalancer.

### Como ver funcionando (Docker)

> ❓Descobrir sobre as portas ao subir com o `--scale pagamento-service=2`.
 
Suba com mais de uma instancia de `pagamento-service`, `estoque-service` e `frete-service`:

- `docker compose up --build --scale pagamento-service=2 --scale estoque-service=2 --scale frete-service=2`

Teste repetidamente pelos endpoints de "whoami" expostos pelo gateway:

- `http://localhost:8080/whoami/pagamento`
- `http://localhost:8080/whoami/estoque`
- `http://localhost:8080/whoami/frete`
- `http://localhost:8080/whoami/vendas`

Em chamadas sequenciais, o `instanceId` deve alternar entre instancias (ex.: round-robin).
