package com.example.pagamento.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoControllerTest {

    private PagamentoController pagamentoController;

    @BeforeEach
    void setUp() throws Exception {
        pagamentoController = new PagamentoController();

        // Injeta failRate = 0 (sem falhas simuladas) via reflexao
        Field failRateField = PagamentoController.class.getDeclaredField("failRate");
        failRateField.setAccessible(true);
        failRateField.setDouble(pagamentoController, 0.0);

        // Injeta delayMs = 0 (sem latencia simulada) via reflexao
        Field delayMsField = PagamentoController.class.getDeclaredField("delayMs");
        delayMsField.setAccessible(true);
        delayMsField.setLong(pagamentoController, 0L);
    }

    @Nested
    @DisplayName("status()")
    class StatusTests {

        @Test
        @DisplayName("deve retornar status OK")
        void deveRetornarStatusOk() {
            Map<String, Object> response = pagamentoController.status();

            assertThat(response).containsEntry("status", "OK");
            assertThat(response).containsEntry("mensagem", "Pagamentos disponíveis");
            assertThat(response).containsEntry("provedor", "Stripe-sandbox");
        }

        @Test
        @DisplayName("deve retornar status OK no endpoint com prefixo")
        void deveRetornarStatusOkNoEndpointComPrefixo() {
            Map<String, Object> response = pagamentoController.statusComPrefixo();

            assertThat(response).containsEntry("status", "OK");
        }
    }

    @Nested
    @DisplayName("pagar()")
    class PagarTests {

        @Test
        @DisplayName("deve processar pagamento com sucesso")
        void deveProcessarPagamentoComSucesso() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", 100.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response).isNotNull();
            assertThat(response.transacaoId()).isNotBlank();
            assertThat(response.status()).isEqualTo("APROVADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-001");
            assertThat(response.valor()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("deve gerar transacaoId unico para cada pagamento")
        void deveGerarTransacaoIdUnicoParaCadaPagamento() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", 100.0);

            PagamentoController.PagamentoResponse r1 = pagamentoController.pagar(request);
            PagamentoController.PagamentoResponse r2 = pagamentoController.pagar(request);

            assertThat(r1.transacaoId()).isNotEqualTo(r2.transacaoId());
        }

        @Test
        @DisplayName("deve retornar 400 quando request e null")
        void deveRetornar400QuandoRequestENull() {
            assertThatThrownBy(() -> pagamentoController.pagar(null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatorio");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando pedidoId e vazio")
        void deveRetornar400QuandoPedidoIdEVazio() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("", 100.0);

            assertThatThrownBy(() -> pagamentoController.pagar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatorio");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando pedidoId e espaco em branco")
        void deveRetornar400QuandoPedidoIdEEspacoEmBranco() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("  ", 100.0);

            assertThatThrownBy(() -> pagamentoController.pagar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatorio");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando valor e menor ou igual a zero")
        void deveRetornar400QuandoValorEMenorOuIgualAZero() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", 0.0);

            assertThatThrownBy(() -> pagamentoController.pagar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("valor deve ser > 0");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando valor e negativo")
        void deveRetornar400QuandoValorENegativo() {
            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", -50.0);

            assertThatThrownBy(() -> pagamentoController.pagar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("valor deve ser > 0");
                    });
        }
    }
}
