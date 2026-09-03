package com.example.pagamento.api;

import com.example.pagamento.domain.model.Transacao;
import com.example.pagamento.domain.port.TransacaoRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link PagamentoController}.
 * Valida processamento de pagamento, idempotência, status e validação de dados.
 */
@ExtendWith(MockitoExtension.class)
class PagamentoControllerTest {

    private PagamentoController pagamentoController;

    @Mock
    private TransacaoRepositoryPort transacaoRepository;

    /**
     * Configura o ambiente de teste com failRate=0 e delayMs=0.
     *
     * @throws Exception se houver erro ao injetar valores via reflexão
     */
    @BeforeEach
    void setUp() throws Exception {
        pagamentoController = new PagamentoController(transacaoRepository);

        Field failRateField = PagamentoController.class.getDeclaredField("failRate");
        failRateField.setAccessible(true);
        failRateField.setDouble(pagamentoController, 0.0);

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
            when(transacaoRepository.buscarPorPedidoId("pedido-001")).thenReturn(Optional.empty());

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", 100.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response).isNotNull();
            assertThat(response.transacaoId()).isNotBlank();
            assertThat(response.status()).isEqualTo("APROVADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-001");
            assertThat(response.valor()).isEqualTo(100.0);
            verify(transacaoRepository).salvar(any(Transacao.class));
        }

        @Test
        @DisplayName("deve retornar transação existente quando pedidoId já foi processado (idempotência)")
        void deveRetornarTransacaoExistenteQuandoPedidoIdJaFoiProcessado() {
            Transacao existente = new Transacao("transacao-abc", "pedido-001", 100.0,
                    "APROVADO", Instant.now());
            when(transacaoRepository.buscarPorPedidoId("pedido-001")).thenReturn(Optional.of(existente));

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-001", 100.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response.transacaoId()).isEqualTo("transacao-abc");
            assertThat(response.status()).isEqualTo("APROVADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-001");
            verify(transacaoRepository, never()).salvar(any());
        }

        @Test
        @DisplayName("deve processar pagamento com pedidoId diferente")
        void deveProcessarPagamentoComPedidoIdDiferente() {
            when(transacaoRepository.buscarPorPedidoId("pedido-002")).thenReturn(Optional.empty());

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-002", 200.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response.transacaoId()).isNotBlank();
            assertThat(response.status()).isEqualTo("APROVADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-002");
            verify(transacaoRepository).salvar(any(Transacao.class));
        }

        @Test
        @DisplayName("deve retornar 400 quando request e null")
        void deveRetornar400QuandoRequestENull() {
            assertThatThrownBy(() -> pagamentoController.pagar(null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatório");
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
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatório");
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
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatório");
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

    @Nested
    @DisplayName("pagar() - Idempotencia")
    class IdempotenciaTests {

        @Test
        @DisplayName("deve retornar transacao existente sem processar falha simulada")
        void deveRetornarTransacaoExistenteSemProcessarFalhaSimulada() throws Exception {
            Field failRateField = PagamentoController.class.getDeclaredField("failRate");
            failRateField.setAccessible(true);
            failRateField.setDouble(pagamentoController, 1.0);

            Transacao existente = new Transacao("tx-idemp-001", "pedido-idemp-001", 100.0,
                    "APROVADO", Instant.now());
            when(transacaoRepository.buscarPorPedidoId("pedido-idemp-001")).thenReturn(Optional.of(existente));

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-idemp-001", 100.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response.transacaoId()).isEqualTo("tx-idemp-001");
            assertThat(response.status()).isEqualTo("APROVADO");
            verify(transacaoRepository, never()).salvar(any());
        }

        @Test
        @DisplayName("deve retornar transacao existente quando DataIntegrityViolationException occurs durante salvar")
        void deveRetornarTransacaoExistenteQuandoDataIntegrityViolationEmSalvar() {
            Transacao existente = new Transacao("tx-concorrencia", "pedido-concorrencia", 200.0,
                    "APROVADO", Instant.now());

            when(transacaoRepository.buscarPorPedidoId("pedido-concorrencia"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existente));
            doThrow(new DataIntegrityViolationException("Duplicate key"))
                    .when(transacaoRepository).salvar(any());

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-concorrencia", 200.0);

            PagamentoController.PagamentoResponse response = pagamentoController.pagar(request);

            assertThat(response.transacaoId()).isEqualTo("tx-concorrencia");
            assertThat(response.status()).isEqualTo("APROVADO");
            assertThat(response.pedidoId()).isEqualTo("pedido-concorrencia");
        }

        @Test
        @DisplayName("deve retornar a mesma transacao quando pagamento e chamado duas vezes com mesmo pedidoId")
        void deveRetornarAMesmaTransacaoQuandoPagamentoEChamadoDuasVezesComMesmoPedidoId() {
            Transacao existente = new Transacao("tx-dup-test", "pedido-dup-test", 150.0,
                    "APROVADO", Instant.now());
            when(transacaoRepository.buscarPorPedidoId("pedido-dup-test"))
                    .thenReturn(Optional.of(existente));

            PagamentoController.PagamentoRequest request =
                    new PagamentoController.PagamentoRequest("pedido-dup-test", 150.0);

            PagamentoController.PagamentoResponse response1 = pagamentoController.pagar(request);
            PagamentoController.PagamentoResponse response2 = pagamentoController.pagar(request);

            assertThat(response1.transacaoId()).isEqualTo(response2.transacaoId());
            assertThat(response1.pedidoId()).isEqualTo(response2.pedidoId());
            assertThat(response1.status()).isEqualTo(response2.status());
            verify(transacaoRepository, never()).salvar(any());
        }
    }
}
