package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.model.StatusPedido;
import com.example.vendas.pedido.entities.PedidoEntity;
import com.example.vendas.pedido.entities.PedidoItemEntity;
import com.example.vendas.pedido.infrastructure.repository.PedidoJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoRepositoryAdapterTest {

    @Mock
    private PedidoJpaRepository jpaRepository;

    @InjectMocks
    private PedidoRepositoryAdapter adapter;

    @Test
    @DisplayName("deve restaurar pedido com status CRIADO corretamente")
    void deveRestaurarPedidoCriado() {
        PedidoEntity entity = buildEntity("pedido-001", StatusPedido.CRIADO, null, null);
        when(jpaRepository.findById("pedido-001")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-001").orElseThrow();

        assertThat(pedido.getPedidoId()).isEqualTo("pedido-001");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getMensagemErro()).isNull();
        assertThat(pedido.getTransacaoId()).isNull();
    }

    @Test
    @DisplayName("deve restaurar pedido com status ESTOQUE_RESERVADO sem marcar como falha")
    void deveRestaurarEstoqueReservadoSemFalha() {
        PedidoEntity entity = buildEntity("pedido-002", StatusPedido.ESTOQUE_RESERVADO, null, null);
        when(jpaRepository.findById("pedido-002")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-002").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ESTOQUE_RESERVADO);
        assertThat(pedido.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve restaurar pedido com status FRETE_CALCULADO sem marcar como falha")
    void deveRestaurarFreteCalculadoSemFalha() {
        PedidoEntity entity = buildEntity("pedido-003", StatusPedido.FRETE_CALCULADO, null, null);
        when(jpaRepository.findById("pedido-003")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-003").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FRETE_CALCULADO);
        assertThat(pedido.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve restaurar pedido com status PAGO e transacaoId corretamente")
    void deveRestaurarPagoComTransacaoId() {
        PedidoEntity entity = buildEntity("pedido-004", StatusPedido.PAGO, "txn-123", null);
        when(jpaRepository.findById("pedido-004")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-004").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(pedido.getTransacaoId()).isEqualTo("txn-123");
        assertThat(pedido.getMensagemErro()).isNull();
    }

    @Test
    @DisplayName("deve restaurar pedido com FALHA_ESTOQUE e mensagem de erro")
    void deveRestaurarFalhaEstoque() {
        PedidoEntity entity = buildEntity("pedido-005", StatusPedido.FALHA_ESTOQUE, null, "SKU nao encontrado");
        when(jpaRepository.findById("pedido-005")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-005").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FALHA_ESTOQUE);
        assertThat(pedido.getMensagemErro()).isEqualTo("SKU nao encontrado");
    }

    @Test
    @DisplayName("deve restaurar pedido com FALHA_FRETE e mensagem de erro")
    void deveRestaurarFalhaFrete() {
        PedidoEntity entity = buildEntity("pedido-006", StatusPedido.FALHA_FRETE, null, "Frete indisponivel");
        when(jpaRepository.findById("pedido-006")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-006").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FALHA_FRETE);
        assertThat(pedido.getMensagemErro()).isEqualTo("Frete indisponivel");
    }

    @Test
    @DisplayName("deve restaurar pedido com FALHA_PAGAMENTO e mensagem de erro")
    void deveRestaurarFalhaPagamento() {
        PedidoEntity entity = buildEntity("pedido-007", StatusPedido.FALHA_PAGAMENTO, null, "Cartao recusado");
        when(jpaRepository.findById("pedido-007")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-007").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FALHA_PAGAMENTO);
        assertThat(pedido.getMensagemErro()).isEqualTo("Cartao recusado");
    }

    @Test
    @DisplayName("deve restaurar pedido com FALHA_TRANSITORIA e mensagem de erro")
    void deveRestaurarFalhaTransitoria() {
        PedidoEntity entity = buildEntity("pedido-008", StatusPedido.FALHA_TRANSITORIA, null, "Timeout no servico");
        when(jpaRepository.findById("pedido-008")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-008").orElseThrow();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FALHA_TRANSITORIA);
        assertThat(pedido.getMensagemErro()).isEqualTo("Timeout no servico");
    }

    @Test
    @DisplayName("deve restaurar itens do pedido com reserva de estoque")
    void deveRestaurarItensComReserva() {
        PedidoEntity entity = buildEntity("pedido-009", StatusPedido.ESTOQUE_RESERVADO, null, null);
        PedidoItemEntity itemEntity = new PedidoItemEntity(
                entity, "SKU-001", "Mouse Gamer", 2, 50.0,
                "reserva-001", null, 0.0, null);
        entity.addItem(itemEntity);
        when(jpaRepository.findById("pedido-009")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-009").orElseThrow();

        assertThat(pedido.getItems()).hasSize(1);
        assertThat(pedido.getItems().get(0).getSku()).isEqualTo("SKU-001");
        assertThat(pedido.getItems().get(0).getQuantidade()).isEqualTo(2);
        assertThat(pedido.getItems().get(0).getReservaId()).isEqualTo("reserva-001");
    }

    @Test
    @DisplayName("deve restaurar itens do pedido com frete calculado")
    void deveRestaurarItensComFrete() {
        PedidoEntity entity = buildEntity("pedido-010", StatusPedido.FRETE_CALCULADO, null, null);
        PedidoItemEntity itemEntity = new PedidoItemEntity(
                entity, "SKU-002", "Teclado Mecânico", 1, 100.0,
                "reserva-002", "frete-002", 25.50, "3 dias");
        entity.addItem(itemEntity);
        when(jpaRepository.findById("pedido-010")).thenReturn(Optional.of(entity));

        Pedido pedido = adapter.buscarPorId("pedido-010").orElseThrow();

        assertThat(pedido.getItems()).hasSize(1);
        assertThat(pedido.getItems().get(0).getFreteId()).isEqualTo("frete-002");
        assertThat(pedido.getItems().get(0).getValorFrete()).isEqualTo(25.50);
        assertThat(pedido.getItems().get(0).getPrazoEntrega()).isEqualTo("3 dias");
    }

    @Test
    @DisplayName("deve retornar vazio quando pedido nao existe")
    void deveRetornarVazioQuandoNaoExiste() {
        when(jpaRepository.findById("inexistente")).thenReturn(Optional.empty());

        Optional<Pedido> result = adapter.buscarPorId("inexistente");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deve salvar pedido convertendo para entity")
    void deveSalvarPedido() {
        Pedido pedido = Pedido.criar("pedido-novo", "01310-100");
        when(jpaRepository.save(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        adapter.salvar(pedido);

        verify(jpaRepository).save(any(PedidoEntity.class));
    }

    @Test
    @DisplayName("deve verificar existencia de pedido")
    void deveVerificarExistencia() {
        when(jpaRepository.existsById("pedido-001")).thenReturn(true);
        when(jpaRepository.existsById("inexistente")).thenReturn(false);

        assertThat(adapter.existsById("pedido-001")).isTrue();
        assertThat(adapter.existsById("inexistente")).isFalse();
    }

    private PedidoEntity buildEntity(String pedidoId, StatusPedido status, String transacaoId, String mensagemErro) {
        PedidoEntity entity = new PedidoEntity(
                pedidoId, "01310-100", status, Instant.now(),
                transacaoId, mensagemErro, null);
        return entity;
    }
}
