package com.example.vendas.pedido.infrastructure.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class PedidoCriadoEventSchemaTest {

    private JsonSchema schema;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        InputStream schemaStream = getClass().getResourceAsStream("/schemas/pedido-criado-event.json");
        assertThat(schemaStream).isNotNull();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        schema = factory.getSchema(schemaStream);
    }

    private Set<ValidationMessage> validate(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return schema.validate(node);
    }

    @Test
    @DisplayName("evento valido deve passar na validacao do schema")
    void eventoValidoDevePassarNaValidacao() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "pedido-001",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 120.50, 241.00)),
                261.00,
                20.00,
                "01310-100"
        );

        String json = objectMapper.writeValueAsString(event);
        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("evento com multiplos itens deve passar na validacao")
    void eventoComMultiplosItensDevePassar() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "pedido-002",
                List.of(
                        new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 120.50, 241.00),
                        new PedidoCriadoEvent.ItemEvent("SKU-DEF", 1, 50.00, 50.00),
                        new PedidoCriadoEvent.ItemEvent("SKU-GHI", 3, 30.00, 90.00)
                ),
                381.00,
                35.00,
                "20040-020"
        );

        String json = objectMapper.writeValueAsString(event);
        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("evento sem pedidoId deve falhar na validacao")
    void eventoSemPedidoIdDeveFalhar() throws Exception {
        String json = """
                {
                  "items": [{"sku": "SKU-ABC", "quantidade": 2, "valorUnitario": 10.0, "subtotal": 20.0}],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
        assertThat(errors.toString()).contains("pedidoId");
    }

    @Test
    @DisplayName("evento sem items deve falhar na validacao")
    void eventoSemItemsDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
        assertThat(errors.toString()).contains("items");
    }

    @Test
    @DisplayName("evento com items vazio deve falhar na validacao")
    void eventoComItemsVazioDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("evento com cepDestino em formato invalido deve falhar")
    void eventoComCepInvalidoDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [{"sku": "SKU-ABC", "quantidade": 2, "valorUnitario": 10.0, "subtotal": 20.0}],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "ABCDEF"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("evento com item sem sku deve falhar")
    void eventoComItemSemSkuDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [{"quantidade": 2, "valorUnitario": 10.0, "subtotal": 20.0}],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
        assertThat(errors.toString()).contains("sku");
    }

    @Test
    @DisplayName("evento com quantidade zero deve falhar")
    void eventoComQuantidadeZeroDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [{"sku": "SKU-ABC", "quantidade": 0, "valorUnitario": 10.0, "subtotal": 20.0}],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("evento com campo adicional deve falhar (additionalProperties=false)")
    void eventoComCampoAdicionalDeveFalhar() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "pedido-001",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 120.50, 241.00)),
                261.00,
                20.00,
                "01310-100"
        );

        String json = objectMapper.writeValueAsString(event)
                .replace("}", ", \"campoExtra\": \"nao permitido\"}");

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("formato antigo (incompativel) deve falhar na validacao")
    void formatoAntigoIncompativelDeveFalhar() throws Exception {
        String formatoAntigo = """
                {
                  "pedidoId": "pedido-001",
                  "sku": "SKU-ABC",
                  "quantidade": 2,
                  "valor": 241.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(formatoAntigo);

        assertThat(errors).isNotEmpty();
    }
}
