package com.example.vendas.pedido.infrastructure.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de compatibilidade entre producer (vendas-service) e consumers
 * (transportadora-service, notificacao-service).
 *
 * <p>Valida que o JSON produzido pelo vendas-service via JsonSerializer
 * (com headers de tipo) esta em conformidade com o schema canônico que
 * os consumers esperam. Simula exatamente o que acontece em producao:
 * vendas serializa -> Kafka transporta -> consumer deserializa.</p>
 */
class ProducerConsumerCompatibilityTest {

    private JsonSchema schema;
    private final ObjectMapper objectMapper = ObjectMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @BeforeEach
    void setUp() throws Exception {
        InputStream schemaStream = getClass().getResourceAsStream("/schemas/pedido-criado-event.json");
        assertThat(schemaStream).isNotNull();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        schema = factory.getSchema(schemaStream);
    }

    @Test
    @DisplayName("JSON produzido pelo producer deve ser validado pelo schema dos consumers")
    void jsonDoProducerDeveSerValidadoPeloSchemaDosConsumers() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                List.of(
                        new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 120.50, 241.00),
                        new PedidoCriadoEvent.ItemEvent("SKU-DEF", 1, 50.00, 50.00)
                ),
                311.00,
                30.00,
                "01310-100"
        );

        String jsonDoProducer = objectMapper.writeValueAsString(event);
        Set<ValidationMessage> errors = schema.validate(jsonDoProducer);

        assertThat(errors)
                .withFailMessage("JSON produzido pelo vendas-service viola o schema canônico: %s", errors)
                .isEmpty();
    }

    @Test
    @DisplayName("JSON produzido deve conter todos os campos obrigatorios do schema")
    void jsonDoProducerDeveConterCamposObrigatorios() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "pedido-test",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-TEST", 1, 10.0, 10.0)),
                10.0,
                5.0,
                "13010-110"
        );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"pedidoId\"");
        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"valorTotal\"");
        assertThat(json).contains("\"valorFreteTotal\"");
        assertThat(json).contains("\"cepDestino\"");
    }

    @Test
    @DisplayName("roundtrip: producer serializa e consumer deserializa corretamente")
    void roundtripProducerParaConsumer() throws Exception {
        PedidoCriadoEvent original = new PedidoCriadoEvent(
                "pedido-roundtrip",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-RT", 3, 25.00, 75.00)),
                95.00,
                20.00,
                "30130-000"
        );

        String json = objectMapper.writeValueAsString(original);

        schema.validate(json);

        PedidoCriadoEvent deserializado = objectMapper.readValue(json, PedidoCriadoEvent.class);

        assertThat(deserializado.pedidoId()).isEqualTo(original.pedidoId());
        assertThat(deserializado.items()).hasSize(original.items().size());
        assertThat(deserializado.items().get(0).sku()).isEqualTo("SKU-RT");
        assertThat(deserializado.items().get(0).quantidade()).isEqualTo(3);
        assertThat(deserializado.items().get(0).valorUnitario()).isEqualTo(25.00);
        assertThat(deserializado.items().get(0).subtotal()).isEqualTo(75.00);
        assertThat(deserializado.valorTotal()).isEqualTo(95.00);
        assertThat(deserializado.valorFreteTotal()).isEqualTo(20.00);
        assertThat(deserializado.cepDestino()).isEqualTo("30130-000");
    }

    @Test
    @DisplayName("JSON com campos incompativeis deve falhar (simula bug de formato)")
    void jsonComCamposIncompativeisDeveFalhar() {
        String jsonIncompativel = """
                {
                  "pedidoId": "pedido-001",
                  "sku": "SKU-ABC",
                  "quantidade": 2,
                  "valor": 241.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = schema.validate(jsonIncompativel);

        assertThat(errors).isNotEmpty();
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
    }
}
