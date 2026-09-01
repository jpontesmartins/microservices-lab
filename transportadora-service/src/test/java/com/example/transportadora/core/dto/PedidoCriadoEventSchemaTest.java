package com.example.transportadora.core.dto;

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
    @DisplayName("evento com formato canonico deve passar na validacao")
    void eventoComFormatoCanonicoDevePassar() throws Exception {
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
    @DisplayName("deserialize do JSON deve gerar objeto valido")
    void deserializeDeveGerarObjetoValido() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [
                    {"sku": "SKU-ABC", "quantidade": 2, "valorUnitario": 120.50, "subtotal": 241.00},
                    {"sku": "SKU-DEF", "quantidade": 1, "valorUnitario": 50.00, "subtotal": 50.00}
                  ],
                  "valorTotal": 311.00,
                  "valorFreteTotal": 30.00,
                  "cepDestino": "20040-020"
                }
                """;

        PedidoCriadoEvent event = objectMapper.readValue(json, PedidoCriadoEvent.class);

        assertThat(event.pedidoId()).isEqualTo("pedido-001");
        assertThat(event.items()).hasSize(2);
        assertThat(event.items().get(0).sku()).isEqualTo("SKU-ABC");
        assertThat(event.items().get(1).sku()).isEqualTo("SKU-DEF");
        assertThat(event.valorTotal()).isEqualTo(311.00);
        assertThat(event.valorFreteTotal()).isEqualTo(30.00);
        assertThat(event.cepDestino()).isEqualTo("20040-020");
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

    @Test
    @DisplayName("evento nulo deve falhar na validacao")
    void eventoNuloDeveFalhar() throws Exception {
        String json = "null";

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("evento com item adicional deve falhar (additionalProperties=false)")
    void eventoComItemAdicionalDeveFalhar() throws Exception {
        String json = """
                {
                  "pedidoId": "pedido-001",
                  "items": [{"sku": "SKU-ABC", "quantidade": 2, "valorUnitario": 10.0, "subtotal": 20.0, "extra": true}],
                  "valorTotal": 20.0,
                  "valorFreteTotal": 5.0,
                  "cepDestino": "01310-100"
                }
                """;

        Set<ValidationMessage> errors = validate(json);

        assertThat(errors).isNotEmpty();
    }
}
