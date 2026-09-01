package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.infrastructure.dto.FreteRequest;
import com.example.vendas.pedido.infrastructure.dto.FreteResponse;
import com.example.vendas.pedido.infrastructure.dto.PagamentoRequest;
import com.example.vendas.pedido.infrastructure.dto.PagamentoResponse;
import com.example.vendas.pedido.infrastructure.dto.ReservaRequest;
import com.example.vendas.pedido.infrastructure.dto.ReservaResponse;
import com.example.vendas.shared.exception.BusinessException;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios do {@link IntegracoesService}.
 * Valida chamadas a servicos downstream, fallbacks e best-effort cancellations.
 */
@ExtendWith(MockitoExtension.class)
class IntegracoesServiceTest {

    @Mock
    private com.example.vendas.pedido.infrastructure.client.EstoqueClient estoqueClient;

    @Mock
    private com.example.vendas.pedido.infrastructure.client.FreteClient freteClient;

    @Mock
    private com.example.vendas.pedido.infrastructure.client.PagamentoClient pagamentoClient;

    @InjectMocks
    private IntegracoesService integracoesService;

    private static FeignException feignExceptionWithStatus(int status) {
        return FeignException.errorStatus("test",
                feign.Response.builder()
                        .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://test",
                                java.util.Map.of(), null, StandardCharsets.UTF_8, new feign.RequestTemplate()))
                        .status(status)
                        .reason("Error " + status)
                        .body("", StandardCharsets.UTF_8)
                        .build());
    }

    @Nested
    @DisplayName("reservarEstoque()")
    class ReservarEstoqueTests {

        @Test
        @DisplayName("deve reservar estoque com sucesso")
        void deveReservarEstoqueComSucesso() {
            ReservaRequest request = new ReservaRequest("pedido-001", "SKU-ABC", 2);
            ReservaResponse response = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            when(estoqueClient.reservar(any(ReservaRequest.class))).thenReturn(response);

            IntegracoesPort.ReservaEstoqueResult result = integracoesService.reservarEstoque("pedido-001", "SKU-ABC", 2);

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

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando estoque-client falha com erro de servidor")
        void deveRetornarFALHA_TRANSITORIAQuandoEstoqueClientFalhaComErroDeServidor() {
            IntegracoesPort.ReservaEstoqueResult fallback = integracoesService.reservaFallback(
                    "pedido-001", "SKU-ABC", 2,
                    new RuntimeException("Estoque indisponivel"));

            assertThat(fallback).isNotNull();
            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.reservaId()).isNull();
        }

        @Test
        @DisplayName("deve lancar BusinessException quando estoque-client falha com erro de negocio (4xx)")
        void deveLancarBusinessExceptionQuandoEstoqueClientFalhaComErroDeNegocio() {
            FeignException businessError = feignExceptionWithStatus(409);

            assertThatThrownBy(() -> integracoesService.reservaFallback(
                    "pedido-001", "SKU-ABC", 2, businessError))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_ESTOQUE");
        }

        @Test
        @DisplayName("deve lancar BusinessException quando FeignException 4xx esta envolvida em CompletionException")
        void deveLancarBusinessExceptionQuandoFeignExceptionEstaEnvolvidaEmCompletionException() {
            FeignException businessError = feignExceptionWithStatus(409);
            CompletionException wrapped = new CompletionException(businessError);

            assertThatThrownBy(() -> integracoesService.reservaFallback(
                    "pedido-001", "SKU-ABC", 2, wrapped))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_ESTOQUE");
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

            IntegracoesPort.FreteResult result = integracoesService.calcularFrete("pedido-001", "SKU-ABC", 2, "01310-100");

            assertThat(result).isNotNull();
            assertThat(result.freteId()).isEqualTo("frete-001");
            assertThat(result.status()).isEqualTo("CALCULADO");
            assertThat(result.valorFrete()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando frete-client falha com erro de servidor")
        void deveRetornarFALHA_TRANSITORIAQuandoFreteClientFalhaComErroDeServidor() {
            IntegracoesPort.FreteResult fallback = integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100",
                    new RuntimeException("Servico de frete indisponivel"));

            assertThat(fallback).isNotNull();
            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.valorFrete()).isEqualTo(0.0);
            assertThat(fallback.freteId()).isNull();
        }

        @Test
        @DisplayName("deve lancar BusinessException quando frete-client falha com erro de negocio (4xx)")
        void deveLancarBusinessExceptionQuandoFreteClientFalhaComErroDeNegocio() {
            FeignException businessError = feignExceptionWithStatus(400);

            assertThatThrownBy(() -> integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100", businessError))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_FRETE");
        }

        @Test
        @DisplayName("deve lancar BusinessException quando FeignException 4xx de frete esta envolvida em CompletionException")
        void deveLancarBusinessExceptionQuandoFeignExceptionFreteEstaEnvolvidaEmCompletionException() {
            FeignException businessError = feignExceptionWithStatus(400);
            CompletionException wrapped = new CompletionException(businessError);

            assertThatThrownBy(() -> integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100", wrapped))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_FRETE");
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

            IntegracoesPort.PagamentoResult result = integracoesService.processarPagamento("pedido-001", 140.50);

            assertThat(result).isNotNull();
            assertThat(result.transacaoId()).isEqualTo("transacao-001");
            assertThat(result.status()).isEqualTo("APROVADO");
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando pagamento-client falha com erro de servidor")
        void deveRetornarFALHA_TRANSITORIAQuandoPagamentoClientFalhaComErroDeServidor() {
            IntegracoesPort.PagamentoResult fallback = integracoesService.pagamentoFallback(
                    "pedido-001", 140.50,
                    new RuntimeException("Servico de pagamento indisponivel"));

            assertThat(fallback).isNotNull();
            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.transacaoId()).isNull();
        }

        @Test
        @DisplayName("deve lancar BusinessException quando pagamento-client falha com erro de negocio (4xx)")
        void deveLancarBusinessExceptionQuandoPagamentoClientFalhaComErroDeNegocio() {
            FeignException businessError = feignExceptionWithStatus(400);

            assertThatThrownBy(() -> integracoesService.pagamentoFallback(
                    "pedido-001", 140.50, businessError))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_PAGAMENTO");
        }

        @Test
        @DisplayName("deve lancar BusinessException quando FeignException 4xx de pagamento esta envolvida em CompletionException")
        void deveLancarBusinessExceptionQuandoFeignExceptionPagamentoEstaEnvolvidaEmCompletionException() {
            FeignException businessError = feignExceptionWithStatus(400);
            CompletionException wrapped = new CompletionException(businessError);

            assertThatThrownBy(() -> integracoesService.pagamentoFallback(
                    "pedido-001", 140.50, wrapped))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_PAGAMENTO");
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

            integracoesService.cancelarFreteBestEffort("frete-001");
        }
    }
}
