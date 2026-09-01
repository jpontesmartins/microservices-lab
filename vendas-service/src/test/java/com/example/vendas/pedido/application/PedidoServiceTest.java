package com.example.vendas.pedido.application;

import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.shared.exception.BusinessException;
import com.example.vendas.shared.exception.TransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios do {@link PedidoService}.
 * Valida o fluxo de criacao de pedido (saga), validacao de dados e busca.
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private IntegracoesPort integracoes;

    @Mock
    private PedidoRepositoryPort pedidoRepository;

    @Mock
    private EventoPublicacaoPort eventoPublicacao;

    @InjectMocks
    private PedidoService pedidoService;

    private CriarPedidoRequest requestValido;

    @BeforeEach
    void setUp() {
        requestValido = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", 2, 120.50)),
                "01310-100");
    }

    @Nested
    @DisplayName("criarPedido()")
    class CriarPedidoTests {

        @Test
        @DisplayName("deve criar pedido com sucesso com fluxo completo")
        void deveCriarPedidoComSucessoComFluxoCompleto() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
            FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
            PagamentoResult pagamento = new PagamentoResult("transacao-001", "APROVADO", 261.0);

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);

            PedidoResponse response = pedidoService.criarPedido(requestValido);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("PAGO");
            assertThat(response.transacaoId()).isEqualTo("transacao-001");
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).sku()).isEqualTo("SKU-ABC");
            assertThat(response.items().get(0).reservaId()).isEqualTo("reserva-001");
            assertThat(response.items().get(0).freteId()).isEqualTo("frete-001");
            assertThat(response.items().get(0).valorFrete()).isEqualTo(20.0);
            assertThat(response.items().get(0).prazoEntrega()).isEqualTo("3 dias uteis");
            assertThat(response.valorFreteTotal()).isEqualTo(20.0);

            verify(integracoes).reservarEstoque(anyString(), eq("SKU-ABC"), eq(2));
            verify(integracoes).calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"));
            verify(integracoes).processarPagamento(anyString(), eq(261.0));
            verify(pedidoRepository, times(4)).salvar(any());
            verify(eventoPublicacao).publicarPedidoCriado(any());
        }

        @Test
        @DisplayName("deve criar pedido com multiplos itens com sucesso")
        void deveCriarPedidoComMultiplosItensComSucesso() {
            CriarPedidoRequest requestMultiplos = new CriarPedidoRequest(
                    List.of(
                            new ItemPedidoRequest("SKU-ABC", 2, 120.50),
                            new ItemPedidoRequest("SKU-DEF", 1, 50.0)),
                    "01310-100");

            ReservaEstoqueResult reserva1 = new ReservaEstoqueResult("reserva-001", "RESERVADO");
            ReservaEstoqueResult reserva2 = new ReservaEstoqueResult("reserva-002", "RESERVADO");
            FreteResult frete1 = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
            FreteResult frete2 = new FreteResult("frete-002", "CALCULADO", 10.0, "2 dias uteis");
            PagamentoResult pagamento = new PagamentoResult("transacao-001", "APROVADO", 321.0);

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva1);
            when(integracoes.reservarEstoque(anyString(), eq("SKU-DEF"), eq(1))).thenReturn(reserva2);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete1);
            when(integracoes.calcularFrete(anyString(), eq("SKU-DEF"), eq(1), eq("01310-100"))).thenReturn(frete2);
            when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);

            PedidoResponse response = pedidoService.criarPedido(requestMultiplos);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("PAGO");
            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).sku()).isEqualTo("SKU-ABC");
            assertThat(response.items().get(1).sku()).isEqualTo("SKU-DEF");
            assertThat(response.valorFreteTotal()).isEqualTo(30.0);

            verify(integracoes).processarPagamento(anyString(), eq(321.0));
            verify(pedidoRepository, times(4)).salvar(any());
            verify(eventoPublicacao).publicarPedidoCriado(any());
        }

        @Test
        @DisplayName("deve salvar pedido e lancar BusinessException quando estoque falha com erro de negocio")
        void deveSalvarPedidoELancarBusinessExceptionQuandoEstoqueFalhaComErroDeNegocio() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2)))
                    .thenThrow(new BusinessException("FALHA_ESTOQUE", "Sem estoque para SKU ABC", null));

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo("FALHA_ESTOQUE"));

            verify(pedidoRepository, times(2)).salvar(any());
            verify(integracoes, never()).calcularFrete(anyString(), anyString(), anyInt(), anyString());
            verify(integracoes, never()).processarPagamento(anyString(), anyDouble());
            verify(eventoPublicacao, never()).publicarPedidoCriado(any());
        }

        @Test
        @DisplayName("deve salvar pedido e lancar TransientException quando estoque retorna null (erro de servidor)")
        void deveLancarTransientExceptionQuandoEstoqueRetornaNull() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(null);

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(pedidoRepository, times(2)).salvar(any());
            verify(eventoPublicacao, never()).publicarPedidoCriado(any());
        }

        @Test
        @DisplayName("deve lancar TransientException quando estoque retorna FALHA_TRANSITORIA e salvar pedido")
        void deveLancarTransientExceptionQuandoEstoqueRetornaRespostaComFALHA_TRANSITORIA() {
            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2)))
                    .thenReturn(new ReservaEstoqueResult(null, "FALHA_TRANSITORIA"));

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(pedidoRepository, times(2)).salvar(any());
            verify(eventoPublicacao, never()).publicarPedidoCriado(any());
        }

        @Test
        @DisplayName("deve salvar pedido e lancar BusinessException quando frete falha com erro de negocio e compensar estoque")
        void deveSalvarPedidoELancarBusinessExceptionQuandoFreteFalhaComErroDeNegocioECompensarEstoque() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                    .thenThrow(new BusinessException("FALHA_FRETE", "CEP de destino invalido", null));

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo("FALHA_FRETE"));

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes, never()).processarPagamento(anyString(), anyDouble());
            verify(pedidoRepository, times(3)).salvar(any());
        }

        @Test
        @DisplayName("deve lancar TransientException quando frete retorna null e compensar estoque")
        void deveLancarTransientExceptionQuandoFreteRetornaNullECompensarEstoque() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(null);

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(pedidoRepository, times(3)).salvar(any());
        }

        @Test
        @DisplayName("deve lancar TransientException quando frete retorna FALHA_TRANSITORIA e compensar estoque")
        void deveLancarTransientExceptionQuandoFreteRetornaRespostaComFALHA_TRANSITORIA() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                    .thenReturn(new FreteResult(null, "FALHA_TRANSITORIA", 0.0, null));

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(pedidoRepository, times(3)).salvar(any());
        }

        @Test
        @DisplayName("deve lancar TransientException quando pagamento retorna null e compensar estoque e frete")
        void deveLancarTransientExceptionQuandoPagamentoRetornaNullECompensarEstoqueEFrete() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
            FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(261.0))).thenReturn(null);

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
            verify(pedidoRepository, times(4)).salvar(any());
        }

        @Test
        @DisplayName("deve lancar TransientException quando pagamento retorna FALHA_TRANSITORIA e compensar estoque e frete")
        void deveLancarTransientExceptionQuandoPagamentoRetornaRespostaComFALHA_TRANSITORIA() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
            FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
            PagamentoResult pagamentoFalhaTransitoria = new PagamentoResult(null, "FALHA_TRANSITORIA", 261.0);

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(261.0))).thenReturn(pagamentoFalhaTransitoria);

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(TransientException.class);

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
            verify(pedidoRepository, times(4)).salvar(any());
        }

        @Test
        @DisplayName("deve salvar pedido e lancar BusinessException quando pagamento falha com erro de negocio e compensar estoque e frete")
        void deveSalvarPedidoELancarBusinessExceptionQuandoPagamentoFalhaComErroDeNegocioECompensarEstoqueEFrete() {
            ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
            FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");

            when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
            when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
            when(integracoes.processarPagamento(anyString(), eq(261.0)))
                    .thenThrow(new BusinessException("FALHA_PAGAMENTO", "Cartao recusado", null));

            assertThatThrownBy(() -> pedidoService.criarPedido(requestValido))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo("FALHA_PAGAMENTO"));

            verify(integracoes).cancelarReservaBestEffort("reserva-001");
            verify(integracoes).cancelarFreteBestEffort("frete-001");
            verify(pedidoRepository, times(4)).salvar(any());
        }
    }

    @Nested
    @DisplayName("criarPedido() - Validacao")
    class ValidacaoTests {

        @Test
        @DisplayName("deve lancar excecao quando request e null")
        void deveLancarExcecaoQuandoRequestENull() {
            assertThatThrownBy(() -> pedidoService.criarPedido(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Body obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando items e vazio")
        void deveLancarExcecaoQuandoItemsEVazio() {
            CriarPedidoRequest request = new CriarPedidoRequest(List.of(), "01310-100");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("items obrigatorio e nao pode ser vazio");
        }

        @Test
        @DisplayName("deve lancar excecao quando items e null")
        void deveLancarExcecaoQuandoItemsENull() {
            CriarPedidoRequest request = new CriarPedidoRequest(null, "01310-100");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("items obrigatorio e nao pode ser vazio");
        }

        @Test
        @DisplayName("deve lancar excecao quando sku do item e vazio")
        void deveLancarExcecaoQuandoSkuDoItemEVazio() {
            CriarPedidoRequest request = new CriarPedidoRequest(
                    List.of(new ItemPedidoRequest("", 1, 100.0)), "01310-100");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("item[0].sku obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando quantidade do item e menor ou igual a zero")
        void deveLancarExcecaoQuandoQuantidadeDoItemEMenorOuIgualAZero() {
            CriarPedidoRequest request = new CriarPedidoRequest(
                    List.of(new ItemPedidoRequest("SKU", 0, 100.0)), "01310-100");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("item[0].quantidade deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando valor do item e menor ou igual a zero")
        void deveLancarExcecaoQuandoValorDoItemEMenorOuIgualAZero() {
            CriarPedidoRequest request = new CriarPedidoRequest(
                    List.of(new ItemPedidoRequest("SKU", 1, 0.0)), "01310-100");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("item[0].valor deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando cepDestino e vazio")
        void deveLancarExcecaoQuandoCepDestinoEVazio() {
            CriarPedidoRequest request = new CriarPedidoRequest(
                    List.of(new ItemPedidoRequest("SKU", 1, 100.0)), "");

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("cepDestino obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando cepDestino e null")
        void deveLancarExcecaoQuandoCepDestinoENull() {
            CriarPedidoRequest request = new CriarPedidoRequest(
                    List.of(new ItemPedidoRequest("SKU", 1, 100.0)), null);

            assertThatThrownBy(() -> pedidoService.criarPedido(request))
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
            PedidoResponse response = pedidoService.buscar("pedido-inexistente");

            assertThat(response).isNull();
        }
    }
}
