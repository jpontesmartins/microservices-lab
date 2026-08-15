package com.example.vendas.integration;

import com.example.vendas.integration.dto.FreteRequest;
import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoRequest;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaRequest;
import com.example.vendas.integration.dto.ReservaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegracoesServiceTest {

    @Mock
    private EstoqueClient estoqueClient;

    @Mock
    private FreteClient freteClient;

    @Mock
    private PagamentoClient pagamentoClient;

    @InjectMocks
    private IntegracoesService integracoesService;

    @Nested
    @DisplayName("reservarEstoque()")
    class ReservarEstoqueTests {

        @Test
        @DisplayName("deve reservar estoque com sucesso")
        void deveReservarEstoqueComSucesso() {
            ReservaRequest request = new ReservaRequest("pedido-001", "SKU-ABC", 2);
            ReservaResponse response = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            when(estoqueClient.reservar(any(ReservaRequest.class))).thenReturn(response);

            ReservaResponse result = integracoesService.reservarEstoque("pedido-001", "SKU-ABC", 2);

            assertThat(result).isNotNull();
            assertThat(result.reservaId()).isEqualTo("reserva-001");
            assertThat(result.status()).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("deve propagar excecao do estoque-client")
        void devePropagarExcecaoDoEstoqueClient() {
            when(estoqueClient.reservar(any(ReservaRequest.class)))
                    .thenThrow(new RuntimeException("Estoque indisponivel"));

            try {
                integracoesService.reservarEstoque("pedido-001", "SKU-ABC", 2);
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).isEqualTo("Estoque indisponivel");
            }
        }
    }

    @Nested
    @DisplayName("calcularFrete()")
    class CalcularFreteTests {

        @Test
        @DisplayName("deve calcular frete com sucesso")
        void deveCalcularFreteComSucesso() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU-ABC", 2, "01310-100");
            FreteResponse response = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");
            when(freteClient.calcular(any(FreteRequest.class))).thenReturn(response);

            FreteResponse result = integracoesService.calcularFrete("pedido-001", "SKU-ABC", 2, "01310-100");

            assertThat(result).isNotNull();
            assertThat(result.freteId()).isEqualTo("frete-001");
            assertThat(result.status()).isEqualTo("CALCULADO");
            assertThat(result.valorFrete()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("deve retornar fallback quando frete-client falha")
        void deveRetornarFallbackQuandoFreteClientFalha() {
            // O fallback e acionado pelo Resilience4j, nao pelo metodo diretamente.
            // Aqui testamos o fallback manualmente.
            FreteResponse fallback = integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100",
                    new RuntimeException("Servico de frete indisponivel"));

            assertThat(fallback).isNotNull();
            assertThat(fallback.status()).isEqualTo("INDISPONIVEL");
            assertThat(fallback.valorFrete()).isEqualTo(0.0);
            assertThat(fallback.freteId()).isNull();
        }
    }

    @Nested
    @DisplayName("processarPagamento()")
    class ProcessarPagamentoTests {

        @Test
        @DisplayName("deve processar pagamento com sucesso")
        void deveProcessarPagamentoComSucesso() {
            PagamentoRequest request = new PagamentoRequest("pedido-001", 140.50);
            PagamentoResponse response = new PagamentoResponse("transacao-001", "APROVADO", "pedido-001", 140.50);
            when(pagamentoClient.pagar(any(PagamentoRequest.class))).thenReturn(response);

            PagamentoResponse result = integracoesService.processarPagamento("pedido-001", 140.50);

            assertThat(result).isNotNull();
            assertThat(result.transacaoId()).isEqualTo("transacao-001");
            assertThat(result.status()).isEqualTo("APROVADO");
        }

        @Test
        @DisplayName("deve retornar fallback quando pagamento-client falha")
        void deveRetornarFallbackQuandoPagamentoClientFalha() {
            PagamentoResponse fallback = integracoesService.pagamentoFallback(
                    "pedido-001", 140.50,
                    new RuntimeException("Servico de pagamento indisponivel"));

            assertThat(fallback).isNotNull();
            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.transacaoId()).isNull();
        }
    }

    @Nested
    @DisplayName("cancelarReservaBestEffort()")
    class CancelarReservaBestEffortTests {

        @Test
        @DisplayName("deve cancelar reserva existente")
        void deveCancelarReservaExistente() {
            integracoesService.cancelarReservaBestEffort("reserva-001");

            verify(estoqueClient).cancelarReserva("reserva-001");
        }

        @Test
        @DisplayName("nao deve chamar client quando reservaId e null")
        void NaoDeveChamarClientQuandoReservaIdENull() {
            integracoesService.cancelarReservaBestEffort(null);

            verify(estoqueClient, org.mockito.Mockito.never()).cancelarReserva(any());
        }

        @Test
        @DisplayName("nao deve chamar client quando reservaId e vazio")
        void NaoDeveChamarClientQuandoReservaIdEVazio() {
            integracoesService.cancelarReservaBestEffort("  ");

            verify(estoqueClient, org.mockito.Mockito.never()).cancelarReserva(any());
        }

        @Test
        @DisplayName("nao deve propagar excecao quando cancelamento falha")
        void NaoDevePropagarExcecaoQuandoCancelamentoFalha() {
            org.mockito.Mockito.doThrow(new RuntimeException("Erro de conexao"))
                    .when(estoqueClient).cancelarReserva("reserva-001");

            // Nao deve lancar excecao (best-effort)
            integracoesService.cancelarReservaBestEffort("reserva-001");
        }
    }

    @Nested
    @DisplayName("cancelarFreteBestEffort()")
    class CancelarFreteBestEffortTests {

        @Test
        @DisplayName("deve cancelar frete existente")
        void deveCancelarFreteExistente() {
            integracoesService.cancelarFreteBestEffort("frete-001");

            verify(freteClient).cancelar("frete-001");
        }

        @Test
        @DisplayName("nao deve chamar client quando freteId e null")
        void NaoDeveChamarClientQuandoFreteIdENull() {
            integracoesService.cancelarFreteBestEffort(null);

            verify(freteClient, org.mockito.Mockito.never()).cancelar(any());
        }

        @Test
        @DisplayName("nao deve chamar client quando freteId e vazio")
        void NaoDeveChamarClientQuandoFreteIdEVazio() {
            integracoesService.cancelarFreteBestEffort("  ");

            verify(freteClient, org.mockito.Mockito.never()).cancelar(any());
        }

        @Test
        @DisplayName("nao deve propagar excecao quando cancelamento de frete falha")
        void NaoDevePropagarExcecaoQuandoCancelamentoDeFreteFalha() {
            org.mockito.Mockito.doThrow(new RuntimeException("Erro de conexao"))
                    .when(freteClient).cancelar("frete-001");

            // Nao deve lancar excecao (best-effort)
            integracoesService.cancelarFreteBestEffort("frete-001");
        }
    }
}
