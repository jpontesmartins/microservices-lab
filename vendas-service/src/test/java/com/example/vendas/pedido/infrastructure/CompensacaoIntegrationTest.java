package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.shared.exception.TransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CompensacaoIntegrationTest {

    @Autowired
    private PedidoService pedidoService;

    @MockBean
    private IntegracoesPort integracoes;

    @Autowired
    private CompensacaoPendenteJpaRepository compensacaoJpaRepository;

    @BeforeEach
    void setUp() {
        compensacaoJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("integracao: quando frete retorna FALHA_TRANSITORIA, compensacao de estoque e criada")
    void quandoFreteRetornaFALHA_TRANSITORIACompensacaoDeEstoqueEhCriada() {
        CriarPedidoRequest request = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", "Mouse Gamer", 2, 120.50)),
                "01310-100");

        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-int-001", "RESERVADO");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100")))
                .thenReturn(new FreteResult(null, "FALHA_TRANSITORIA", 0.0, null));

        assertThatThrownBy(() -> pedidoService.criarPedido(request, "int-comp-001", null))
                .isInstanceOf(TransientException.class);

        List<CompensacaoPendenteEntity> pendentes = compensacaoJpaRepository
                .findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE);

        assertThat(pendentes).hasSize(1);
        assertThat(pendentes.get(0).getPedidoId()).isEqualTo("int-comp-001");
        assertThat(pendentes.get(0).getTipo()).isEqualTo(TipoCompensacao.ESTOQUE);
        assertThat(pendentes.get(0).getRefId()).isEqualTo("reserva-int-001");
        assertThat(pendentes.get(0).getAttempts()).isEqualTo(0);
        assertThat(pendentes.get(0).getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("integracao: quando pagamento retorna FALHA_TRANSITORIA, compensacoes de estoque e frete sao criadas")
    void quandoPagamentoRetornaFALHA_TRANSITORIACompensacoesSaoCriadas() {
        CriarPedidoRequest request = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", "Mouse Gamer", 2, 120.50)),
                "01310-100");

        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-int-002", "RESERVADO");
        FreteResult frete = new FreteResult("frete-int-002", "CALCULADO", 20.0, "3 dias uteis");

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble()))
                .thenReturn(new PagamentoResult(null, "FALHA_TRANSITORIA", 261.0));

        assertThatThrownBy(() -> pedidoService.criarPedido(request, "int-comp-002", null))
                .isInstanceOf(TransientException.class);

        List<CompensacaoPendenteEntity> pendentes = compensacaoJpaRepository
                .findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE);

        assertThat(pendentes).hasSize(2);

        boolean temEstoque = pendentes.stream()
                .anyMatch(c -> c.getTipo() == TipoCompensacao.ESTOQUE && "reserva-int-002".equals(c.getRefId()));
        boolean temFrete = pendentes.stream()
                .anyMatch(c -> c.getTipo() == TipoCompensacao.FRETE && "frete-int-002".equals(c.getRefId()));

        assertThat(temEstoque).isTrue();
        assertThat(temFrete).isTrue();
    }

    @Test
    @DisplayName("integracao: quando pedido e criado com sucesso, NENHUMA compensacao e criada")
    void quandoPedidoCriadoComSucessoNenhumaCompensacaoEhCriada() {
        CriarPedidoRequest request = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", "Mouse Gamer", 2, 120.50)),
                "01310-100");

        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-int-003", "RESERVADO");
        FreteResult frete = new FreteResult("frete-int-003", "CALCULADO", 20.0, "3 dias uteis");
        PagamentoResult pagamento = new PagamentoResult("transacao-int-003", "APROVADO", 261.0);

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);

        var response = pedidoService.criarPedido(request, "int-comp-003", null);

        assertThat(response.status()).isEqualTo("PAGO");
        assertThat(compensacaoJpaRepository.count()).isZero();
    }
}
