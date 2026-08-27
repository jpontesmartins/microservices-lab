package com.example.vendas.core;

import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import com.example.vendas.integration.BusinessException;
import com.example.vendas.integration.IntegracoesService;
import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link PedidoCore}.
 * Valida o fluxo de criação de pedido (saga), validação de dados e busca.
 */
@ExtendWith(MockitoExtension.class)
class PedidoCoreTest {

    @Mock
    private IntegracoesService integracoes;

    @InjectMocks
    private PedidoCore pedidoCore;

    private CriarPedidoRequest requestValido;

    /**
     * Configura o ambiente de teste com um request valido.
     */
    @BeforeEach
    void setUp() {
        requestValido = new CriarPedidoRequest("SKU-ABC", 2, 120.50, "01310-100");
    }

    @Nested
    @DisplayName("criarPedido()")
    class CriarPedidoTests {

        @Test
        @DisplayName("deve criar pedido com sucesso com fluxo completo")
        void deveCriarPedidoComSucessoComFluxoCompleto() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            FreteResponse frete = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");
            PagamentoResponse pagamento = new PagamentoResponse("transacao-001", "APROVADO", "pedido-001", 140.50);

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("PAGO");
            assertThat(response.reservaId()).isEqualTo("reserva-001");
            assertThat(response.freteId()).isEqualTo("frete-001");
            assertThat(response.valorFrete()).isEqualTo(20.0);
            assertThat(response.prazoEntrega()).isEqualTo("3 dias uteis");
            assertThat(response.transacaoId()).isEqualTo("transacao-001");

            verify(integracoes).reservarEstoque(anyString(), eq("SKU-ABC"), eq(2));
            verify(integracoes).calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"));
            verify(integracoes).processarPagamento(anyString(), eq(140.50)); // 120.50 + 20.0
        }

        @Test
        @DisplayName("deve retornar FALHA_ESTOQUE quando estoque falha com erro de negocio")
        void deveRetornarFALHA_ESTOQUEQuandoEstoqueFalhaComErroDeNegocio() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2)))
                    .thenThrow(new BusinessException("FALHA_ESTOQUE", null));

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_ESTOQUE");
            assertThat(response.reservaId()).isNull();
            verify(integracoes, never()).calcularFrete(anyString(), anyString(), anyInt(), anyString());
            verify(integracoes, never()).processarPagamento(anyString(), anyDouble());
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando estoque retorna null (erro de servidor)")
        void deveRetornarFALHA_TRANSITORIAQuandoEstoqueRetornaNull() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(null);

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando estoque retorna resposta com status FALHA_TRANSITORIA")
        void deveRetornarFALHA_TRANSITORIAQuandoEstoqueRetornaRespostaComFALHA_TRANSITORIA() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2)))
                    .thenReturn(new ReservaResponse(null, "FALHA_TRANSITORIA", "SKU-ABC", 2, "pedido-001"));

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(response.reservaId()).isNull();
        }

        @Test
        @DisplayName("deve retornar FALHA_FRETE quando frete falha com erro de negocio e compensar estoque")
        void deveRetornarFALHA_FRETEQuandoFreteFalhaComErroDeNegocioECompensarEstoque() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                    .thenThrow(new BusinessException("FALHA_FRETE", null));

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_FRETE");
            assertThat(response.reservaId()).isEqualTo("reserva-001");
            assertThat(response.freteId()).isNull();
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes, never()).processarPagamento(anyString(), anyDouble());
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando frete retorna null (erro de servidor) e compensar estoque")
        void deveRetornarFALHA_TRANSITORIAQuandoFreteRetornaNullECompensarEstoque() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(null);

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando frete retorna resposta com status FALHA_TRANSITORIA e compensar estoque")
        void deveRetornarFALHA_TRANSITORIAQuandoFreteRetornaRespostaComFALHA_TRANSITORIA() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                    .thenReturn(new FreteResponse(null, "FALHA_TRANSITORIA", "pedido-001", 0.0, null));

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando pagamento retorna null (erro de servidor) e compensar estoque e frete")
        void deveRetornarFALHA_TRANSITORIAQuandoPagamentoRetornaNullECompensarEstoqueEFrete() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            FreteResponse frete = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(140.50))).thenReturn(null);

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(response.reservaId()).isEqualTo("reserva-001");
            assertThat(response.freteId()).isEqualTo("frete-001");
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA quando pagamento retorna resposta com status FALHA_TRANSITORIA e compensar estoque e frete")
        void deveRetornarFALHA_TRANSITORIAQuandoPagamentoRetornaRespostaComFALHA_TRANSITORIA() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            FreteResponse frete = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");
            PagamentoResponse pagamentoFalhaTransitoria = new PagamentoResponse(null, "FALHA_TRANSITORIA", "pedido-001", 140.50);

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(140.50))).thenReturn(pagamentoFalhaTransitoria);

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(response.reservaId()).isEqualTo("reserva-001");
            assertThat(response.freteId()).isEqualTo("frete-001");
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
        }

        @Test
        @DisplayName("deve retornar FALHA_PAGAMENTO quando pagamento falha com erro de negocio e compensar estoque e frete")
        void deveRetornarFALHA_PAGAMENTOQuandoPagamentoFalhaComErroDeNegocioECompensarEstoqueEFrete() {
            ReservaResponse reserva = new ReservaResponse("reserva-001", "RESERVADO", "SKU-ABC", 2, "pedido-001");
            FreteResponse frete = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(140.50)))
                    .thenThrow(new BusinessException("FALHA_PAGAMENTO", null));

            PedidoResponse response = pedidoCore.criarPedido(requestValido);

            assertThat(response.status()).isEqualTo("FALHA_PAGAMENTO");
            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
        }
    }

    @Nested
    @DisplayName("criarPedido() - Validacao")
    class ValidacaoTests {

        @Test
        @DisplayName("deve lancar excecao quando request e null")
        void deveLancarExcecaoQuandoRequestENull() {
            assertThatThrownBy(() -> pedidoCore.criarPedido(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Body obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando sku e vazio")
        void deveLancarExcecaoQuandoSkuEVazio() {
            CriarPedidoRequest request = new CriarPedidoRequest("", 1, 100.0, "01310-100");

            assertThatThrownBy(() -> pedidoCore.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sku obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando quantidade e menor ou igual a zero")
        void deveLancarExcecaoQuandoQuantidadeEMenorOuIgualAZero() {
            CriarPedidoRequest request = new CriarPedidoRequest("SKU", 0, 100.0, "01310-100");

            assertThatThrownBy(() -> pedidoCore.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("quantidade deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando valor e menor ou igual a zero")
        void deveLancarExcecaoQuandoValorEMenorOuIgualAZero() {
            CriarPedidoRequest request = new CriarPedidoRequest("SKU", 1, 0.0, "01310-100");

            assertThatThrownBy(() -> pedidoCore.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("valor deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando cepDestino e vazio")
        void deveLancarExcecaoQuandoCepDestinoEVazio() {
            CriarPedidoRequest request = new CriarPedidoRequest("SKU", 1, 100.0, "");

            assertThatThrownBy(() -> pedidoCore.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("cepDestino obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando cepDestino e null")
        void deveLancarExcecaoQuandoCepDestinoENull() {
            CriarPedidoRequest request = new CriarPedidoRequest("SKU", 1, 100.0, null);

            assertThatThrownBy(() -> pedidoCore.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("cepDestino obrigatorio");
        }
    }

    @Nested
    @DisplayName("buscar()")
    class BuscarTests {

        @Test
        @DisplayName("deve retornar null quando pedido nao existe")
        void deveRetornarNullQuandoPedidoNaoExiste() {
            PedidoResponse response = pedidoCore.buscar("pedido-inexistente");

            assertThat(response).isNull();
        }
    }
}
