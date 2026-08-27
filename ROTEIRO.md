# Roteiro de Teste - Ecossistema de Microservices

## Arquitetura

```
Cliente -> API Gateway (:8080) -> vendas-service (:8081)
                                    ├── estoque-service (:8082)
                                    ├── frete-service (:8084)
                                    └── pagamento-service (:8083)

                                    ┌─ transportadora-service (:8085)  [consumer]
              Kafka (:9092) ────────┤
                                    └─ notificação-service (:8086)   [consumer]

Todos se registram no discovery-server (Eureka) (:8761)
```

**Mensageria:** Apache Kafka (:9092) com Zookeeper (:2181)
**Observabilidade:** Prometheus (:9090) | Grafana (:3000) | Zipkin (:9411)

### Pub/Sub com Kafka

O padrão **Pub/Sub** (Publicar/Subscrever) é implementado via Apache Kafka:

```
vendas-service (Producer)
       │
       ▼
   Kafka Broker
   Topic: pedido-criado
       │
       ├──► transportadora-service (Consumer - group: transportadora-group)
       │      "Pedido sendo processado pela transportadora"
       │
        └──► notificação-service (Consumer - group: notificacao-group)
               "Notificação enviada ao usuário"
```

**Fluxo:**
1. `vendas-service` cria e paga um pedido
2. Após pagamento aprovado, publica evento `PedidoCriado` na topic `pedido-criado`
3. `transportadora-service` e `notificação-service` consomem a mensagem independentemente
4. Cada serviço processa em seu grupo de consumo (group-id), garantindo que apenas um consumer por grupo receba cada mensagem

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker + Docker Compose
- (Opcional) curl ou ferramenta HTTP como Postman/Insomnia

---

## Passo 1 - Rodar os testes unitários

```bash
# Descubra todos os serviços com testes e rode em paralelo:
cd vendas-service && mvn test && cd ..
cd estoque-service && mvn test && cd ..
cd frete-service && mvn test && cd ..
cd pagamento-service && mvn test && cd ..
cd transportadora-service && mvn test && cd ..
cd notificacao-service && mvn test && cd ..
```

---

## Passo 2 - Subir a infraestrutura de observabilidade

```bash
cd observability
docker compose -f observability.yml up -d
cd ..
```

Verifique:
- Zipkin: http://localhost:9411
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (usuário: `admin`, senha: `1234`)

---

## Passo 3 - Subir todos os microservices via Docker Compose

```bash
docker compose up --build -d
```

Aguarde todos ficarem healthy. Verifique com:

```bash
docker compose ps
```

Serviços esperados:
| Container | Porta |
|-----------|-------|
| discovery | 8761 |
| api-gateway | 8080 |
| vendas-service | 8081 |
| estoque-service | 8082 |
| pagamento-service | 8083 |
| frete-service | 8084 |
| transportadora-service | 8085 |
| notificação-service | 8086 |
| zookeeper | 2181 |
| kafka | 9092 |

---

## Passo 4 - Verificar que todos registraram no Eureka

Acesse o dashboard do Eureka:
- http://localhost:8761

Deve listar: `API-GATEWAY`, `VENDAS-SERVICE`, `ESTOQUE-SERVICE`, `PAGAMENTO-SERVICE`, `FRETE-SERVICE`, `TRANSPORTADORA-SERVICE`, `NOTIFICACAO-SERVICE`

---

## Passo 5 - Testar o Whoami de cada serviço (via Gateway)

```bash
# Vendas
curl http://localhost:8080/whoami/vendas

# Estoque
curl http://localhost:8080/whoami/estoque

# Pagamento
curl http://localhost:8080/whoami/pagamento

# Frete
curl http://localhost:8080/whoami/frete

# Transportadora
curl http://localhost:8080/whoami/transportadora

# Notificação
curl http://localhost:8080/whoami/notificação
```

Cada resposta deve retornar `{service, instanceId, port}`.

---

## Passo 6 - Testar os endpoints diretos (sem Gateway)

```bash
# Listar itens em estoque
curl http://localhost:8082/estoque/itens

# Status de pagamento
curl http://localhost:8083/pagamento/status

# Listar vendas
curl http://localhost:8081/vendas
```

---

## Passo 7 - Criar um Pedido (fluxo completo)

Este é o fluxo principal: o `vendas-service` orquestra estoque → frete → pagamento.

```bash
curl -X POST http://localhost:8080/vendas/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "ABC-123",
    "quantidade": 2,
    "valor": 199.90,
    "cepDestino": "01310-100"
  }'
```

**Resposta esperada (sucesso):**
```json
{
  "pedidoId": "<uuid>",
  "status": "CRIADO",
  "reservaEstoque": { "status": "CONFIRMADA", ... },
  "frete": { "status": "CALCULADO", "valor": ..., ... },
  "pagamento": { "status": "PAGO", ... }
}
```

---

## Passo 8 - Consultar o pedido criado

```bash
# Substitua <pedidoId> pelo UUID retornado no Passo 7
curl http://localhost:8080/vendas/pedidos/<pedidoId>
```

---

## Passo 9 - Testar cenários de falha

### 9.1 - Estoque insuficiente

```bash
curl -X POST http://localhost:8080/vendas/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "XYZ-789",
    "quantidade": 999,
    "valor": 50.00,
    "cepDestino": "01310-100"
  }'
```

Espera-se erro de estoque e compensação (cancelamento de frete/reserva).

### 9.2 - SKU inexistente

```bash
curl -X POST http://localhost:8080/vendas/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "NAO-EXISTE",
    "quantidade": 1,
    "valor": 100.00,
    "cepDestino": "01310-100"
  }'
```

---

## Passo 10 - Testar Circuit Breaker (Resilience4j)

### 10.1 - Ativar falhas forçadas no pagamento

Altere o `failRate` no `pagamento-service/src/main/resources/application.yml`:
```yaml
pagamento:
  failRate: 1.0   # 100% de falha
```

Reconstrua e reinicie:
```bash
docker compose up --build -d pagamento-service
```

### 10.2 - Disparar múltiplas requisições

```bash
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "Request $i: HTTP %{http_code}\n" \
    -X POST http://localhost:8080/vendas/pedidos \
    -H "Content-Type: application/json" \
    -d '{"sku":"ABC-123","quantidade":1,"valor":10.00,"cepDestino":"01310-100"}'
done
```

Após 5 chamadas com falha, o Circuit Breaker deve abrir e retornar `FALHA_TRANSITORIA` (fallback) imediatamente.

### 10.3 - Verificar métricas do circuit breaker

```bash
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.state
```

---

## Passo 11 - Testar Cancelamento

```bash
# Cancelar reserva de estoque (direto)
curl -X DELETE http://localhost:8082/estoque/reservas/<reservaId>

# Cancelar frete (direto)
curl -X DELETE http://localhost:8084/frete/calcular/<freteId>
```

---

## Passo 12 - Observabilidade

### 12.1 - Métricas Prometheus

```bash
# Métricas do API Gateway
curl http://localhost:8080/actuator/prometheus

# Métricas do vendas-service
curl http://localhost:8081/actuator/prometheus
```

### 12.2 - Grafana

1. Acesse http://localhost:3000
2. Login: `admin` / `admin`
3. Adicione um data source Prometheus com URL `http://prometheus:9090`
4. Importe um dashboard ou crie painéis usando as métricas Micrometer

### 12.3 - Tracing (Zipkin)

1. Acesse http://localhost:9411
2. Busque por serviços (api-gateway, vendas-service, etc.)
3. Clique em "Find Traces" para ver o fluxo completo de uma requisição

---

## Passo 13 - Testar Load Balancing

```bash
# Chame o whoami várias vezes via gateway para ver diferentes instâncias
for i in $(seq 1 10); do
  curl -s http://localhost:8080/whoami/vendas | python -m json.tool
  echo "---"
done
```

Se houver múltiplas instâncias, o `instanceId` e/ou `port` devem variar.

---

## Passo 14 - Health Checks

```bash
# Verificar saúde de todos os serviços
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

---

## Passo 15 - Parar tudo

```bash
# Parar microservices
docker compose down

# Parar observabilidade
cd observability && docker compose -f observability.yml down
```

---

## Passo 16 - Testar Kafka Pub/Sub

### 16.1 - Verificar que o Kafka está rodando

```bash
docker compose ps kafka
```

### 16.2 - Criar um pedido e observar os logs

```bash
curl -X POST http://localhost:8080/vendas/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "ABC-123",
    "quantidade": 1,
    "valor": 199.90,
    "cepDestino": "01310-100"
  }'
```

### 16.3 - Verificar logs dos consumers

```bash
# Transportadora - deve mostrar "Pedido sendo processado pela transportadora"
docker compose logs transportadora-service

# Notificação - deve mostrar "Notificação enviada ao usuário"
docker compose logs notificacao-service
```

---

## Mapa de Portas

| Serviço | Porta |
|---------|-------|
| discovery-server (Eureka) | 8761 |
| api-gateway | 8080 |
| vendas-service | 8081 |
| estoque-service | 8082 |
| pagamento-service | 8083 |
| frete-service | 8084 |
| transportadora-service | 8085 |
| notificacao-service | 8086 |
| Zookeeper | 2181 |
| Kafka | 9092 |
| Prometheus | 9090 |
| Grafana | 3000 |
| Zipkin | 9411 |

## Dados de Estoque Iniciais

| SKU | Produto | Quantidade |
|-----|---------|------------|
| ABC-123 | Teclado Mecânico | 42 |
| XYZ-789 | Mouse Gamer | 15 |

## CEPs de Exemplo

| CEP | Região | Frete estimado |
|-----|--------|----------------|
| 01310-100 | SP (capital) | Valor base |
| 30130-000 | Sudeste (BH) | +20% |
| 80010-000 | Sul (CWB) | +30% |
| 69005-000 | Norte (MAN) | +50% |
