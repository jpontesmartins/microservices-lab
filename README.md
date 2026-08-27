# Microservices Lab - Java 21 (Spring Cloud + Kafka)

Este repositório contém 6 aplicações Spring Boot independentes, montando um ecossistema completo para estudo de microsserviços: Service Discovery (Eureka), API Gateway (Spring Cloud Gateway), comunicação assíncrona via Kafka, resiliência com Circuit Breaker (Resilience4j) e observabilidade (Micrometer + Zipkin + Prometheus).

## Ecossistema de Arquitetura de Microservices - Pontos de Estudo

### Padrões Arquiteturais

| Padrão | Descrição | Onde é aplicado neste projeto |
|--------|-----------|-------------------------------|
| **Service Discovery** | Serviço de registry onde instâncias se registram e descobrem umas pelas outras via nome lógico | Netflix Eureka (`discovery-server`) |
| **API Gateway** | Ponto de entrada único que roteia, autentica, faz rate-limit e logging | Spring Cloud Gateway (`api-gateway`) |
| **Saga (Orquestração)** | Fluxo distribuído com passos sequenciais e transações compensatórias em caso de falha | `vendas-service` orquestra estoque -> frete -> pagamento com compensação best-effort |
| **Circuit Breaker** | Mecanismo de resiliência que interrompe chamadas a serviços com falhas para evitar cascata | Resilience4j com 3 instâncias (`estoque`, `frete`, `pagamento`) |
| **Event-Driven (Pub/Sub)** | Comunicação assíncrona via eventos publicados em topics Kafka | `vendas-service` publica `PedidoCriado`; `transportadora` e `notificação` consomem |
| **Client-Side Load Balancing** | Distribuição de carga no lado do cliente usando nome lógico do serviço | Spring Cloud LoadBalancer (substitui Ribbon) |
| **Compensating Transaction** | Ação inversa para desfazer efeitos de passos anteriores quando um passo falha | Cancelamento de reserva de estoque e frete após falha no pagamento |

### Conceitos de Resiliência

| Conceito | Descrição |
|----------|-----------|
| **Timeout** | Tempo máximo de espera por uma resposta de serviço remoto |
| **Retry** | Tentativa automática de reexecutar uma operação que falhou |
| **Fallback** | Alternativa executada quando o serviço remoto está indisponível |
| **Rate Limiting** | Controle de taxa de requisições para proteger serviços contra sobrecarga |
| **Graceful Degradation** | Capacidade do sistema de continuar funcionando com funcionalidade reduzida |

### Padrões de Comunicação

| Padrão | Descrição | Tecnologia utilizada |
|--------|-----------|---------------------|
| **Synchronous REST** | Chamadas HTTP bloqueantes entre serviços | Spring Cloud OpenFeign |
| **Asynchronous Messaging** | Comunicação via filas de mensagens | Apache Kafka (topic `pedido-criado`) |

### Observabilidade

| Pilar | Descrição | Ferramenta |
|-------|-----------|------------|
| **Metrics** | Coleta de métricas de performance e uso | Micrometer + Prometheus |
| **Tracing** | Rastreamento de requisições entre serviços | Micrometer Tracing (Brave) + Zipkin |
| **Logging** | Registros estruturados com correlação de traces | SLF4J com traceId/spanId no pattern |
| **Health Checks** | Verificação de saúde dos serviços | Spring Boot Actuator (`/actuator/health`) |

### Infraestrutura e DevOps

| Conceito | Descrição | Tecnologia utilizada |
|----------|-----------|---------------------|
| **Containerization** | Empacotamento de serviços em containers | Docker + Dockerfile |
| **Orchestration** | Gerenciamento e composição de containers | Docker Compose |
| **Multi-Instance Deployment** | Múltiplas instâncias do mesmo serviço para alta disponibilidade | `docker compose --scale` |
| **Configuration Management** | Gerenciamento de configurações por ambiente | `application.yml` + profiles Spring |

### Stack Tecnológica Completa

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Cloud | Spring Cloud 2023.0.1 |
| Discovery | Netflix Eureka |
| Gateway | Spring Cloud Gateway |
| Inter-service | OpenFeign + Spring Cloud LoadBalancer |
| Circuit Breaker | Resilience4j |
| Messaging | Apache Kafka (Confluent 7.5.0) + Kafka UI |
| Metrics | Micrometer + Prometheus |
| Tracing | Brave/Zipkin |
| Monitoring | Grafana |
| Build | Maven |
| Container | Docker + Docker Compose |

## Projetos

### `discovery-server`

Função: servidor Eureka (service registry).

- Mantém um "catálogo" de instâncias registradas (quem está no ar e onde).
- Os outros projetos usam este servidor para se registrar e para descobrir outros serviços via nome (serviceId).
- Porta (DEV/TST): `8761`
- Console: `http://localhost:8761`

### `api-gateway`

Função: ponto de entrada único (reverse proxy) para os microserviços.

- Recebe chamadas HTTP do cliente e roteia para os serviços usando Discovery + load-balancing (`lb://...`).
- Ajuda a concentrar concerns comuns: roteamento, autenticação, rate limit, logging, etc. (neste projeto estamos focando em roteamento).
- Porta (DEV/TST): `8080`
- Rotas configuradas:
  - `GET http://localhost:8080/vendas` -> `lb://vendas-service/vendas`
  - `POST http://localhost:8080/vendas/pedidos` -> `lb://vendas-service/vendas/pedidos`
  - `GET http://localhost:8080/estoque/itens` -> `lb://estoque-service/estoque/itens`
  - `GET http://localhost:8080/pagamento/status` -> `lb://pagamento-service/pagamento/status`
  - `POST http://localhost:8080/frete/calcular` -> `lb://frete-service/frete/calcular`

### `vendas-service`

Função: microserviço A (exemplo de "vendas") e orquestrador do fluxo de pedido neste lab.

- Fornece um endpoint simples para listar vendas.
- Registra-se no Eureka com o nome `vendas-service` (definido em `spring.application.name`).
- Implementa um fluxo de negócio simples (Processamento de Pedido):
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

Função: microserviço B (exemplo de "estoque").

- Fornece um endpoint simples para listar itens em estoque.
- Registra-se no Eureka com o nome `estoque-service`.
- Porta (DEV/TST): `8082`
- Endpoint principal:
  - `GET http://localhost:8082/estoque/itens`
  - `POST http://localhost:8082/estoque/reservas`
  - `DELETE http://localhost:8082/estoque/reservas/{reservaId}`

### `pagamento-service`

Função: microserviço C (exemplo de "pagamentos").

- Fornece um endpoint simples de status do serviço de pagamentos.
- Registra-se no Eureka com o nome `pagamento-service`.
- Porta (DEV/TST): `8083`
- Endpoint principal:
  - `GET http://localhost:8083/pagamento/status`
  - `POST http://localhost:8083/pagamento/pagamentos`

### `frete-service`

Função: microserviço D (exemplo de "frete/envio").

- Calcula o valor e prazo de entrega baseado no CEP de destino.
- Registra-se no Eureka com o nome `frete-service`.
- Porta (DEV/TST): `8084`
- Endpoint principal:
  - `POST http://localhost:8084/frete/calcular`
  - `DELETE http://localhost:8084/frete/calcular/{freteId}`
- Simula falhas e latência via `frete.failRate` e `frete.delayMs` (application.yml).

## Fluxo de Pedido (Inter-service)

O objetivo é simular dependência real entre serviços usando Service Discovery.

1. Cliente chama o Gateway: `POST /vendas/pedidos`
2. `vendas-service` chama `estoque-service` via Eureka (nome do serviço, sem URL hardcoded):
   - `POST http://estoque-service/estoque/reservas`
3. Se o estoque reservar, `vendas-service` chama `frete-service` via Eureka:
   - `POST http://frete-service/frete/calcular`
4. Se o frete calcular, `vendas-service` chama `pagamento-service` via Eureka:
   - `POST http://pagamento-service/pagamento/pagamentos`
5. Se o pagamento falhar, `vendas-service` tenta compensar:
   - `DELETE http://estoque-service/estoque/reservas/{reservaId}`
   - `DELETE http://frete-service/frete/calcular/{freteId}`

### Exemplo de requisição

`POST http://localhost:8080/vendas/pedidos`

Body:

```json
{ "sku": "ABC-123", "quantidade": 1, "valor": 120.50, "cepDestino": "01310-100" }
```

## Circuit Breaker (Resilience4j)

O `vendas-service` usa Resilience4j Circuit Breaker em chamadas de estoque, frete e pagamento, protegendo contra falhas em cascata e latência elevada.

### Configuração das Instâncias

Cada circuit breaker (`estoque`, `frete` e `pagamento`) é configurado via `application.yml`:

| Parâmetro | Valor | Explicação |
|-----------|-------|------------|
| `slidingWindowType` | COUNT_BASED | Janela deslizante contabiliza **quantidade** de chamadas (TIME_BASED contabiliza tempo). |
| `slidingWindowSize` | 10 | Quantidade de chamadas na janela para calcular a taxa de falha. |
| `minimumNumberOfCalls` | 5 | Mínimo de chamadas antes de avaliar a taxa (evita abrir com poucos dados). |
| `failureRateThreshold` | 50 | Percentual de falhas que dispara a transição para OPEN. |
| `slowCallRateThreshold` | 60 | Percentual de chamadas lentas que também dispara a transição para OPEN. |
| `slowCallDurationThreshold` | 2s | Chamadas com duração superior são consideradas lentas (slow calls). |
| `waitDurationInOpenState` | 10s | Tempo que o circuit breaker fica OPEN antes de tentar HALF_OPEN. |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Chamadas permitidas no estado HALF_OPEN para testar se o serviço voltou. |
| `automaticTransitionFromOpenToHalfOpenEnabled` | true | Transição automática de OPEN para HALF_OPEN após waitDuration (sem precisar de chamada). |
| `recordExceptions` | IOException, TimeoutException, HttpServerErrorException, ResourceAccessException | Exceções registradas como falhas no cálculo da taxa. |
| `ignoreExceptions` | BusinessException | Erros de negócio (4xx do downstream) que **não** contam como falhas e **não** acionam o fallback. |

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

- **CLOSED**: Chamadas passam normalmente. Taxa de falha é monitorada.
- **OPEN**: Chamadas são rejeitadas imediatamente (fallback acionado).
- **HALF_OPEN**: Chamadas de teste são permitidas. Se OK, volta pra CLOSED; senão, volta pra OPEN.

### Distinguindo Erro de Negócio de Erro de Servidor

Os fallbacks distinguem dois tipos de falha:

- **Erro de servidor** (5xx, timeout, conexão, CircuitBreaker OPEN): o fallback retorna `FALHA_TRANSITORIA`. O `PedidoCore` seta `FALHA_TRANSITORIA` no pedido e aciona compensação.
- **Erro de negócio** (4xx do downstream, ex: "sem estoque"): o fallback lança `BusinessException` (configurada em `ignoreExceptions`). O `PedidoCore` captura e seta `FALHA_ESTOQUE`, `FALHA_FRETE` ou `FALHA_PAGAMENTO` conforme o serviço.

| Método | Erro de Servidor (fallback) | Erro de Negócio (BusinessException) |
|--------|----------------------------|--------------------------------------|
| `reservarEstoque()` | `FALHA_TRANSITORIA` | `FALHA_ESTOQUE` |
| `calcularFrete()` | `FALHA_TRANSITORIA` | `FALHA_FRETE` |
| `processarPagamento()` | `FALHA_TRANSITORIA` | `FALHA_PAGAMENTO` |

### Como Testar

1. Configure falhas no `frete-service` ou `pagamento-service` via `application.yml`:
   - `frete.failRate=0.8` (80% de falhas simuladas no frete)
   - `frete.delayMs=3000` (latência simulada de 3s no frete)
   - `pagamento.failRate=0.8` (80% de falhas simuladas no pagamento)
   - `pagamento.delayMs=3000` (latência simulada de 3s no pagamento)

2. Após 5 chamadas (minimumNumberOfCalls), o circuit breaker correspondente deve abrir.

3. Chamadas subsequentes acionam o fallback sem chamar o serviço remoto.

4. Após 10s (waitDuration), o circuit breaker entra em HALF_OPEN e testa novamente.

- [X] Observe métricas em `GET /actuator/metrics` e `GET /actuator/prometheus` (em cada serviço).

## Observabilidade (Actuator)

Os serviços expõem endpoints Actuator básicos para facilitar diagnóstico e integração com o Eureka UI:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/prometheus`

No gateway, o Actuator também está presente e os endpoints expostos incluem `health` e `info`.

## Monitoramento Kafka (Kafka UI)

O projeto inclui o **Kafka UI** para visualização das topics, mensagens, consumer groups e offsets do Kafka.

- Acesse: `http://localhost:8089`
- Container: `kafka-ui`

### O que é possível visualizar

| Recurso | Descrição |
|---------|-----------|
| **Topics** | Lista de todas as topics, particições e configurações |
| **Messages** | Visualização de mensagens em tempo real com serialização JSON |
| **Consumer Groups** | Status dos grupos de consumo e offsets lag |


## Logs e Tracing

Os serviços agora registram logs por etapa do fluxo:

- entrada no gateway, rota escolhida e tempo total da chamada;
- validação e criação do pedido no `vendas-service`;
- reserva e cancelamento de estoque no `estoque-service`;
- cálculo e cancelamento de frete no `frete-service`;
- simulação, aprovação e falhas do pagamento no `pagamento-service`.

Os logs usam `traceId` e `spanId` no pattern, então quando o Zipkin estiver ativo fica mais fácil correlacionar uma mesma requisição entre serviços.

## Configuração do Eureka (DEV vs TST)

Todos os clientes Eureka (gateway + microserviços) usam a URL do registry via:

- `EUREKA_URI` (variável de ambiente), com default para DEV: `http://localhost:8761/eureka`

No `docker-compose.yml`, cada container recebe:

- `EUREKA_URI=http://discovery:8761/eureka`

## Como executar

### DEV (rodar um a um)

1. Suba o `discovery-server`.
2. Suba o `api-gateway` e os microserviços que quiser (em terminais separados).
3. Teste via gateway:
   - `GET http://localhost:8080/vendas`
   - `POST http://localhost:8080/vendas/pedidos`
   - `GET http://localhost:8080/estoque/itens`
   - `GET http://localhost:8080/pagamento/status`
   - `POST http://localhost:8080/frete/calcular`

### DEV (múltiplas instâncias: Load Balancer)

> ❗ Não consegui ainda configurar o PushGateway. Sem pushgateway, sem envio das métricas das instâncias que não tem porta fixa.
> Os únicos serviços com porta fixa são o `api-gateway` e o `vendas-service`.
> Ambos estão sendo monitorados.

Para simular mais de 1 instância localmente (sem conflito de porta), use o profile `dev` no `estoque-service`, `pagamento-service` e `frete-service` (ele usa `server.port=0`).

Em terminais diferentes, no mesmo serviço:

- `pagamento-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`
- `estoque-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`
- `frete-service`: execute duas vezes com `SPRING_PROFILES_ACTIVE=dev`

Comando para rodar com o profile `dev`:  
`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`

Para visualizar o balanceamento de carga, chame repetidamente:

- `http://localhost:8080/whoami/pagamento`
- `http://localhost:8080/whoami/estoque`

### TST (subir tudo de uma vez)

Na raiz do repositório:

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

Este ecossistema usa Spring Cloud LoadBalancer para distribuir chamadas quando houver mais de uma instância do mesmo serviço registrada no Eureka:

- Gateway: rotas `lb://...` passam pelo LoadBalancer.
- Vendas: chamadas Feign para `estoque-service`, `frete-service` e `pagamento-service` passam pelo LoadBalancer.

### Como ver funcionando (Docker)

> ❓Descobrir sobre as portas ao subir com o `--scale pagamento-service=2`.
 
Suba com mais de uma instância de `pagamento-service`, `estoque-service` e `frete-service`:

- `docker compose up --build --scale pagamento-service=2 --scale estoque-service=2 --scale frete-service=2`

Teste repetidamente pelos endpoints de "whoami" expostos pelo gateway:

- `http://localhost:8080/whoami/pagamento`
- `http://localhost:8080/whoami/estoque`
- `http://localhost:8080/whoami/frete`
- `http://localhost:8080/whoami/vendas`

Em chamadas sequenciais, o `instanceId` deve alternar entre instâncias (ex.: round-robin).
