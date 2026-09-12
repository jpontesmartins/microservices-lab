package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.model.StatusPedido;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.infrastructure.repository.PedidoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
class PedidoRepositoryAdapterIntegrationTest {

    @Autowired
    private PedidoRepositoryPort pedidoRepository;

    @Autowired
    private PedidoJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido CRIADO com roundtrip no banco")
    void deveSalvarERestaurarPedidoCriado() {
        Pedido pedido = Pedido.criar("int-001", "01310-100");
        pedido.adicionarItem(com.example.vendas.pedido.domain.model.ItemPedido.criar("SKU-A", "Produto A", 1, 99.9));

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-001").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(restaurado.getMensagemErro()).isNull();
        assertThat(restaurado.getTransacaoId()).isNull();
        assertThat(restaurado.getItems()).hasSize(1);
        assertThat(restaurado.getItems().get(0).getSku()).isEqualTo("SKU-A");
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido ESTOQUE_RESERVADO sem corromper status")
    void deveSalvarERestaurarEstoqueReservado() {
        Pedido pedido = Pedido.criar("int-002", "02000-000");
        pedido.reservarEstoque("reserva-int-001");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-002").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.ESTOQUE_RESERVADO);
        assertThat(restaurado.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido FRETE_CALCULADO sem corromper status")
    void deveSalvarERestaurarFreteCalculado() {
        Pedido pedido = Pedido.criar("int-003", "30000-000");
        pedido.reservarEstoque("reserva-int-002");
        pedido.calcularFrete();

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-003").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.FRETE_CALCULADO);
        assertThat(restaurado.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido PAGO com transacaoId")
    void deveSalvarERestaurarPago() {
        Pedido pedido = Pedido.criar("int-004", "40000-000");
        pedido.reservarEstoque("reserva-int-003");
        pedido.calcularFrete();
        pedido.confirmarPagamento("txn-int-001");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-004").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(restaurado.getTransacaoId()).isEqualTo("txn-int-001");
        assertThat(restaurado.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido FALHA_ESTOQUE com mensagem de erro")
    void deveSalvarERestaurarFalhaEstoque() {
        Pedido pedido = Pedido.criar("int-005", "50000-000");
        pedido.marcarFalha(StatusPedido.FALHA_ESTOQUE, "SKU inexistente");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-005").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.FALHA_ESTOQUE);
        assertThat(restaurado.getMensagemErro()).isEqualTo("SKU inexistente");
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido FALHA_PAGAMENTO com mensagem de erro")
    void deveSalvarERestaurarFalhaPagamento() {
        Pedido pedido = Pedido.criar("int-006", "60000-000");
        pedido.marcarFalha(StatusPedido.FALHA_PAGAMENTO, "Cartao recusado");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-006").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.FALHA_PAGAMENTO);
        assertThat(restaurado.getMensagemErro()).isEqualTo("Cartao recusado");
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido FALHA_TRANSITORIA com mensagem de erro")
    void deveSalvarERestaurarFalhaTransitoria() {
        Pedido pedido = Pedido.criar("int-007", "70000-000");
        pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA, "Timeout servico externo");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-007").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.FALHA_TRANSITORIA);
        assertThat(restaurado.getMensagemErro()).isEqualTo("Timeout servico externo");
    }

    @Test
    @DisplayName("deve salvar e restaurar pedido FALHA_FRETE com mensagem de erro")
    void deveSalvarERestaurarFalhaFrete() {
        Pedido pedido = Pedido.criar("int-008", "80000-000");
        pedido.marcarFalha(StatusPedido.FALHA_FRETE, "CEP invalido");

        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-008").orElseThrow();

        assertThat(restaurado.getStatus()).isEqualTo(StatusPedido.FALHA_FRETE);
        assertThat(restaurado.getMensagemErro()).isEqualTo("CEP invalido");
    }

    @Test
    @DisplayName("deve sobrescrever status antigo ao re-salvar pedido existente")
    void deveSobrescreverStatusAoResalvar() {
        Pedido pedido = Pedido.criar("int-009", "90000-000");
        pedidoRepository.salvar(pedido);

        Pedido restaurado = pedidoRepository.buscarPorId("int-009").orElseThrow();
        restaurado.reservarEstoque("reserva-int-004");
        pedidoRepository.salvar(restaurado);

        Pedido reRestaurado = pedidoRepository.buscarPorId("int-009").orElseThrow();

        assertThat(reRestaurado.getStatus()).isEqualTo(StatusPedido.ESTOQUE_RESERVADO);
    }
}
