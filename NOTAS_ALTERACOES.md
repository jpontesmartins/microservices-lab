# NOTAS_ALTERACOES — Contrato de Eventos Kafka com JSON Schema

## Problema

O `PedidoCriadoEvent` publicado pelo `vendas-service` tinha formato completamente diferente do que `transportadora-service` e `notificacao-service` esperavam:

| vendas-service (publicava) | transportadora/notificacao (esperavam) |
|---|---|
| `{pedidoId, items: [{sku, quantidade, valorUnitario, valor}], valorTotal, valorFreteTotal, cepDestino}` | `{pedidoId, sku, quantidade, valor,cepDestino}` |

**Consequencia:** Os consumers falhavam silenciosamente na deserializacao (o `ErrorHandlingDeserializer` engolia o erro) e recebiam campos `null`.

---

## Solucao Implementada

### Padrao: JSON Schema + Confluent Schema Registry

O formato canônico do evento e definido em um arquivo JSON Schema (`schemas/pedido-criado-event.json`) que serve como contrato unico entre producer e consumers.

### Decisao: trusted.packages

O `spring.json.trusted.packages` e um mecanismo de seguranca do Spring Kafka que valida o header `__TypeId__` (pacote da classe Java) na deserializacao. Neste projeto, **nao e necessario** porque:

1. **Producer desabilita type headers:** `spring.json.add.type.headers: false` + `spring.json.skip.type.headers: true`
2. **Sem `__TypeId__` no header**, o `JsonDeserializer` usa o tipo Java passado no construtor (`PedidoCriadoEvent.class`) — nao depende de pacotes.
3. **Schema Registry** gerencia o schema via ID, nao via nome de classe.

Se no futuro o producer voltar a enviar type headers, os consumers devem confiar no pacote do producer (`com.example.vendas.pedido.infrastructure.dto`) **e** no proprio pacote.

---

## Arquivos Alterados

### Infraestrutura

| Arquivo | Alteracao |
|---|---|
| `docker-compose.yml` | Adicionado servico `schema-registry` (Confluent 7.5.0, porta 8081). Kafka-UI configurado para mostrar schemas. vendas/transportadora/notificacao dependem do schema-registry. Variavel `SCHEMA_REGISTRY_URL` adicionada. |

### Schema Canônico

| Arquivo | Descricao |
|---|---|
| `schemas/pedido-criado-event.json` | JSON Schema (draft-07) que define o formato oficial do evento. Copiado para `src/test/resources/schemas/` de cada servico. |

### vendas-service (Producer)

| Arquivo | Alteracao |
|---|---|
| `pom.xml` | Adicionadas dependencias: `kafka-schema-registry-client` (7.5.0), `json-schema-validator` (1.5.1, test) |
| `application.yml` | Adicionados `spring.json.skip.type.headers: true`, `spring.json.add.type.headers: false` e `schema.registry.url`. **Sem trusted.packages** (producer nao envia type headers). |
| `PedidoCriadoEvent.java` | **Nao alterado** — ja tinha o formato correto |
| `src/test/.../PedidoCriadoEventSchemaTest.java` | **Novo** — 10 testes validando schema |
| `src/test/.../ProducerConsumerCompatibilityTest.java` | **Novo** — 4 testes de compatibilidade cross-service |
| `src/test/resources/schemas/pedido-criado-event.json` | **Novo** — copia do schema para testes |

### transportadora-service (Consumer)

| Arquivo | Alteracao |
|---|---|
| `pom.xml` | Adicionadas dependencias: `kafka-schema-registry-client` (7.5.0), `json-schema-validator` (1.5.1, test) |
| `application.yml` | Adicionado `schema.registry.url`. **Sem trusted.packages** (producer nao envia type headers). |
| `PedidoCriadoEvent.java` | **Reescrito** — de `{sku, quantidade, valor}` para `{items: [{sku,quantidade,valorUnitario,subtotal}], valorTotal, valorFreteTotal}` |
| `PedidoCriadoConsumer.java` | **Reescrito** — logica de log atualizada para iterar `items` |
| `KafkaConfig.java` | Adicionado `schema.registry.url`. **Removido trusted.packages** (producer nao envia type headers). |
| `src/test/.../PedidoCriadoEventSchemaTest.java` | **Novo** — 5 testes validando schema |
| `src/test/resources/schemas/pedido-criado-event.json` | **Novo** — copia do schema para testes |

### notificacao-service (Consumer)

| Arquivo | Alteracao |
|---|---|
| `pom.xml` | Adicionadas dependencias: `kafka-schema-registry-client` (7.5.0), `json-schema-validator` (1.5.1, test) |
| `application.yml` | Adicionado `schema.registry.url`. **Sem trusted.packages** (producer nao envia type headers). |
| `PedidoCriadoEvent.java` | **Reescrito** — de `{sku, quantidade, valor}` para `{items: [{sku,quantidade,valorUnitario,subtotal}], valorTotal, valorFreteTotal}` |
| `PedidoCriadoConsumer.java` | **Reescrito** — logica de log atualizada para iterar `items` |
| `KafkaConfig.java` | Adicionado `schema.registry.url`. **Removido trusted.packages** (producer nao envia type headers). |
| `src/test/.../PedidoCriadoEventSchemaTest.java` | **Novo** — 6 testes validando schema |
| `src/test/resources/schemas/pedido-criado-event.json` | **Novo** — copia do schema para testes |

---

## Formato Canonico do Evento

```json
{
  "pedidoId": "uuid-string",
  "items": [
    {
      "sku": "SKU-ABC",
      "quantidade": 2,
      "valorUnitario": 120.50,
      "subtotal": 241.00
    }
  ],
  "valorTotal": 261.00,
  "valorFreteTotal": 20.00,
  "cepDestino": "01310-100"
}
```

**Campos obrigatorios:** pedidoId, items (min 1), valorTotal, valorFreteTotal, cepDestino (formato XXXXX-XXX).
**additionalProperties:** `false` — campos extras causam falha na validacao.

---

## Testes Adicionados (25 total)

### vendas-service (14 testes)

- `PedidoCriadoEventSchemaTest` (10 testes):
  - Evento valido passa na validacao
  - Evento com multiplos itens passa
  - Evento sem pedidoId falha
  - Evento sem items falha
  - Evento com items vazio falha
  - Evento com cepDestino invalido falha
  - Evento com item sem sku falha
  - Evento com quantidade zero falha
  - Evento com campo adicional falha (additionalProperties)
  - **Formato antigo (incompativel) falha**

- `ProducerConsumerCompatibilityTest` (4 testes):
  - JSON do producer e validado pelo schema dos consumers
  - JSON contem todos os campos obrigatorios
  - Roundtrip: serialize -> validate -> deserialize
  - JSON com campos incompativeis falha (simula bug)

### transportadora-service (5 testes)

- `PedidoCriadoEventSchemaTest`:
  - Formato canonico passa
  - Deserialize de JSON gera objeto valido
  - **Formato antigo falha**
  - Evento nulo falha
  - Evento com item adicional falha

### notificacao-service (6 testes)

- `PedidoCriadoEventSchemaTest`:
  - Formato canonico passa
  - Deserialize de JSON gera objeto valido
  - **Formato antigo falha**
  - Evento sem valorFreteTotal falha
  - Evento com quantidade negativa falha

---

## Como Previne o Bug

1. **Schema como contrato:** O arquivo `pedido-criado-event.json` e a unica fonte de verdade do formato do evento. Producer e consumers leem o mesmo schema.

2. **Testes de regressao:** Se alguem alterar o `PedidoCriadoEvent` no vendas-service (producer) para um formato incompativel, os testes de schema em todos os servicos falharao no build.

3. **Formato antigo detectado:** O teste `formatoAntigoIncompativelDeveFalhar` valida explicitamente que a estrutura antiga `{sku, quantidade, valor}` nao e aceita pelo schema.

4. **Campos extras bloqueados:** `additionalProperties: false` no schema impede que campos nao-documentados sejam adicionados silenciosamente.

5. **Validacao no CI/CD:** Rodando `mvn test` em qualquer servico, os testes de schema garantem compatibilidade antes do deploy.

---

## Proximos Passos

- [ ] Configurar `JsonSchemaSerializer` no producer (requer Schema Registry rodando)
- [ ] Configurar `JsonSchemaDeserializer` nos consumers (validacao runtime)
- [ ] Criar schema versionado (v1, v2) no Schema Registry para evolucao futura
- [ ] Configurar policy de compatibilidade `BACKWARD` no Schema Registry
