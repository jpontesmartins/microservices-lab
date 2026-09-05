package com.example.vendas.pedido.application;

import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.shared.exception.BusinessException;
import com.example.vendas.shared.exception.TransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@ExtendWith(MockitoExtension.class)
class PedidoServiceCompensacaoTest {

    @Mock
    private IntegracoesPort integracoes;

    @Mock
    private PedidoRepositoryPort pedidoRepository;

    @Mock
    private EventoPublicacaoPort eventoPublicacao;

    @Mock
    private CompensacaoRepositoryPort compensacaoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private CriarPedidoRequest requestValido;

    @BeforeEach
    void setUp() {
        requestValido = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", 2, 120.50)),
                "01310-100");
    }

    @Test
    @DisplayName("quando frete falha, compensacao de estoque deve ser registrada na tabela")
    void quandoFreteFalhaCompensacaoDeEstoqueDeveSerRegistrada() {
        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                .thenThrow(new BusinessException("FALHA_FRETE", "CEP invalido", null));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, null))
                .isInstanceOf(BusinessException.class);

        verify(compensacaoRepository).salvarCompensacao(
                anyString(), eq(TipoCompensacao.ESTOQUE), eq("reserva-001"), eq(3));
        verify(compensacaoRepository, never()).salvarCompensacao(
                anyString(), eq(TipoCompensacao.FRETE), anyString(), anyInt());
    }

    @Test
    @DisplayName("quando pagamento falha, compensacoes de estoque e frete devem ser registradas")
    void quandoPagamentoFalhaCompensacoesDeEstoqueEFreteDevemSerRegistradas() {
        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
        FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble()))
                .thenThrow(new BusinessException("FALHA_PAGAMENTO", "Saldo insuficiente", null));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, null))
                .isInstanceOf(BusinessException.class);

        verify(compensacaoRepository).salvarCompensacao(
                anyString(), eq(TipoCompensacao.ESTOQUE), eq("reserva-001"), eq(3));
        verify(compensacaoRepository).salvarCompensacao(
                anyString(), eq(TipoCompensacao.FRETE), eq("frete-001"), eq(3));
    }

    @Test
    @DisplayName("quando pagamento retorna FALHA_TRANSITORIA, compensacoes devem ser registradas")
    void quandoPagamentoRetornaFALHA_TRANSITORIACompensacoesDevemSerRegistradas() {
        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
        FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble()))
                .thenReturn(new PagamentoResult(null, "FALHA_TRANSITORIA", 261.0));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, null))
                .isInstanceOf(TransientException.class);

        verify(compensacaoRepository).salvarCompensacao(
                anyString(), eq(TipoCompensacao.ESTOQUE), eq("reserva-001"), eq(3));
        verify(compensacaoRepository).salvarCompensacao(
                anyString(), eq(TipoCompensacao.FRETE), eq("frete-001"), eq(3));
    }

    @Test
    @DisplayName("quando estoque falha, NENHUMA compensacao deve ser registrada")
    void quandoEstoqueFalhaNenhumaCompensacaoDeveSerRegistrada() {
        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2)))
                .thenThrow(new BusinessException("FALHA_ESTOQUE", "Sem estoque", null));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, null))
                .isInstanceOf(BusinessException.class);

        verify(compensacaoRepository, never()).salvarCompensacao(
                anyString(), any(TipoCompensacao.class), anyString(), anyInt());
    }

    @Test
    @DisplayName("quando pedido e criado com sucesso, NENHUMA compensacao deve ser registrada")
    void quandoPedidoCriadoComSucessoNenhumaCompensacaoDeveSerRegistrada() {
        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
        FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
        PagamentoResult pagamento = new PagamentoResult("transacao-001", "APROVADO", 261.0);

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);

        var response = pedidoService.criarPedido(requestValido, null);

        assertThat(response.status()).isEqualTo("PAGO");
        verify(compensacaoRepository, never()).salvarCompensacao(
                anyString(), any(TipoCompensacao.class), anyString(), anyInt());
    }

    @Test
    @DisplayName("compensacao com multiplos itens deve registrar compensacao para cada item")
    void compensacaoComMultiplosItensDeveRegistrarParaCadaItem() {
        CriarPedidoRequest requestMultiplos = new CriarPedidoRequest(
                List.of(
                        new ItemPedidoRequest("SKU-ABC", 2, 120.50),
                        new ItemPedidoRequest("SKU-DEF", 1, 50.0)),
                "01310-100");

        ReservaEstoqueResult reserva1 = new ReservaEstoqueResult("reserva-001", "RESERVADO");
        ReservaEstoqueResult reserva2 = new ReservaEstoqueResult("reserva-002", "RESERVADO");
        FreteResult frete1 = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
        FreteResult frete2 = new FreteResult("frete-002", "CALCULADO", 10.0, "2 dias uteis");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva1);
        when(integracoes.reservarEstoque(anyString(), eq("SKU-DEF"), eq(1))).thenReturn(reserva2);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete1);
        when(integracoes.calcularFrete(anyString(), eq("SKU-DEF"), eq(1), eq("01310-100"))).thenReturn(frete2);
        when(integracoes.processarPagamento(anyString(), anyDouble()))
                .thenReturn(new PagamentoResult(null, "FALHA_TRANSITORIA", 321.0));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestMultiplos, null))
                .isInstanceOf(TransientException.class);

        verify(compensacaoRepository, times(2)).salvarCompensacao(
                anyString(), eq(TipoCompensacao.ESTOQUE), anyString(), eq(3));
        verify(compensacaoRepository, times(2)).salvarCompensacao(
                anyString(), eq(TipoCompensacao.FRETE), anyString(), eq(3));
    }
}
