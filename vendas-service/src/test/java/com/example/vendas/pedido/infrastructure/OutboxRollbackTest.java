package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.infrastructure.repository.OutboxEventoJpaRepository;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Rollback(false)
class OutboxRollbackTest {

    @Autowired
    private PedidoService pedidoService;

    @MockBean
    private IntegracoesPort integracoes;

    @MockBean
    private EventoPublicacaoPort eventoPublicacao;

    @Autowired
    private OutboxEventoJpaRepository outboxJpaRepository;

    private CriarPedidoRequest requestValido;

    @BeforeEach
    void setUp() {
        requestValido = new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", 2, 120.50)),
                "01310-100");

        ReservaEstoqueResult reserva = new ReservaEstoqueResult("reserva-001", "RESERVADO");
        FreteResult frete = new FreteResult("frete-001", "CALCULADO", 20.0, "3 dias uteis");
        PagamentoResult pagamento = new PagamentoResult("transacao-001", "APROVADO", 261.0);

        when(integracoes.reservarEstoque(anyString(), eq("SKU-ABC"), eq(2))).thenReturn(reserva);
        when(integracoes.calcularFrete(anyString(), eq("SKU-ABC"), eq(2), eq("01310-100"))).thenReturn(frete);
        when(integracoes.processarPagamento(anyString(), anyDouble())).thenReturn(pagamento);
    }

    @Test
    @DisplayName("rollback: se o eventoPublicacao falhar, o pedido NAO deve ser persistido")
    void rollbackPedidoNaoDeveSerPersistido() {
        doThrow(new RuntimeException("Simulando falha ao salvar no outbox"))
                .when(eventoPublicacao).publicarPedidoCriado(any(Pedido.class));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, "rollback-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulando falha ao salvar no outbox");

        var response = pedidoService.buscar("rollback-001");
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("rollback: se o eventoPublicacao falhar, o outbox NAO deve ter registros")
    void rollbackOutboxNaoDeveTerRegistros() {
        doThrow(new RuntimeException("Simulando falha ao salvar no outbox"))
                .when(eventoPublicacao).publicarPedidoCriado(any(Pedido.class));

        assertThatThrownBy(() -> pedidoService.criarPedido(requestValido, "rollback-002"))
                .isInstanceOf(RuntimeException.class);

        assertThat(outboxJpaRepository.count()).isZero();
    }
}
