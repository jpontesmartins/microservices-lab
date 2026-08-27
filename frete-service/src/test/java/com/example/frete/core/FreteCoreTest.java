package com.example.frete.core;

import com.example.frete.core.dto.FreteRequest;
import com.example.frete.core.dto.FreteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link FreteCore}.
 * Valida cálculo de frete por região, cancelamento e unicidade de IDs.
 */
class FreteCoreTest {

    private FreteCore freteCore;

    /**
     * Configura o ambiente de teste com uma nova instancia de FreteCore.
     */
    @BeforeEach
    void setUp() {
        freteCore = new FreteCore();
    }

    @Nested
    @DisplayName("calcular()")
    class CalcularTests {

        @Test
        @DisplayName("deve calcular frete com sucesso para CEP de SP")
        void deveCalcularFreteComSucessoParaCepDeSp() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU-ABC", 2, "01310-100");

            FreteResponse response = freteCore.calcular(request);

            assertThat(response).isNotNull();
            assertThat(response.freteId()).isNotBlank();
            assertThat(response.status()).isEqualTo("CALCULADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-001");
            assertThat(response.valorFrete()).isEqualTo(20.0); // 10 + (2 * 5)
            assertThat(response.prazoEntrega()).isEqualTo("3 dias uteis");
        }

        @Test
        @DisplayName("deve calcular frete com sucesso para CEP da regiao Sudeste")
        void deveCalcularFreteComSucessoParaCepDaRegiaoSudeste() {
            FreteRequest request = new FreteRequest("pedido-002", "SKU-XYZ", 1, "22041-080");

            FreteResponse response = freteCore.calcular(request);

            assertThat(response.status()).isEqualTo("CALCULADO");
            assertThat(response.valorFrete()).isEqualTo(15.0); // 10 + (1 * 5)
            assertThat(response.prazoEntrega()).isEqualTo("4 dias uteis");
        }

        @Test
        @DisplayName("deve calcular frete com sucesso para CEP da regiao Sul")
        void deveCalcularFreteComSucessoParaCepDaRegiaoSul() {
            FreteRequest request = new FreteRequest("pedido-003", "SKU-DEF", 3, "80010-000");

            FreteResponse response = freteCore.calcular(request);

            assertThat(response.status()).isEqualTo("CALCULADO");
            assertThat(response.valorFrete()).isEqualTo(25.0); // 10 + (3 * 5)
            assertThat(response.prazoEntrega()).isEqualTo("5 dias uteis");
        }

        @Test
        @DisplayName("deve calcular frete com sucesso para CEP da regiao Norte")
        void deveCalcularFreteComSucessoParaCepDaRegiaoNorte() {
            FreteRequest request = new FreteRequest("pedido-004", "SKU-GHI", 1, "66010-000");

            FreteResponse response = freteCore.calcular(request);

            assertThat(response.status()).isEqualTo("CALCULADO");
            assertThat(response.valorFrete()).isEqualTo(15.0); // 10 + (1 * 5)
            assertThat(response.prazoEntrega()).isEqualTo("7 dias uteis");
        }

        @Test
        @DisplayName("deve calcular valor do frete baseado na quantidade")
        void deveCalcularValorDoFreteBaseadoNaQuantidade() {
            FreteRequest request1 = new FreteRequest("p1", "SKU", 1, "01310-100");
            FreteRequest request5 = new FreteRequest("p2", "SKU", 5, "01310-100");

            FreteResponse response1 = freteCore.calcular(request1);
            FreteResponse response5 = freteCore.calcular(request5);

            assertThat(response1.valorFrete()).isEqualTo(15.0);  // 10 + (1 * 5)
            assertThat(response5.valorFrete()).isEqualTo(35.0);  // 10 + (5 * 5)
        }

        @Test
        @DisplayName("deve gerar freteId unico para cada calculo")
        void deveGerarFreteIdUnicoParaCadaCalculo() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU", 1, "01310-100");

            FreteResponse response1 = freteCore.calcular(request);
            FreteResponse response2 = freteCore.calcular(request);

            assertThat(response1.freteId()).isNotEqualTo(response2.freteId());
        }
    }

    @Nested
    @DisplayName("cancelar()")
    class CancelarTests {

        @Test
        @DisplayName("deve cancelar frete existente")
        void deveCancelarFreteExistente() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU", 1, "01310-100");
            FreteResponse response = freteCore.calcular(request);

            boolean resultado = freteCore.cancelar(response.freteId());

            assertThat(resultado).isTrue();
        }

        @Test
        @DisplayName("deve retornar false ao cancelar frete inexistente")
        void deveRetornarFalseAoCancelarFreteInexistente() {
            boolean resultado = freteCore.cancelar("frete-inexistente");

            assertThat(resultado).isFalse();
        }

        @Test
        @DisplayName("deve cancelar apenas o frete especificado")
        void deveCancelarApenasOFreteEspecificado() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU", 1, "01310-100");
            FreteResponse response1 = freteCore.calcular(request);
            FreteResponse response2 = freteCore.calcular(request);

            freteCore.cancelar(response1.freteId());

            // response1 foi cancelado, response2 continua existindo
            boolean resultado2 = freteCore.cancelar(response2.freteId());
            assertThat(resultado2).isTrue();
        }
    }
}
