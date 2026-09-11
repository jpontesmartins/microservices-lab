package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.ItemPedido;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.entities.PedidoEntity;
import com.example.vendas.pedido.entities.PedidoItemEntity;
import com.example.vendas.pedido.infrastructure.repository.PedidoJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoRepositoryAdapter implements PedidoRepositoryPort {

    private final PedidoJpaRepository jpaRepository;

    public PedidoRepositoryAdapter(PedidoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void salvar(Pedido pedido) {
        if (jpaRepository.existsById(pedido.getPedidoId())) {
            PedidoEntity existing = jpaRepository.findById(pedido.getPedidoId()).orElseThrow();
            existing.setStatus(pedido.getStatus());
            existing.setTransacaoId(pedido.getTransacaoId());
            existing.setMensagemErro(pedido.getMensagemErro());

            for (ItemPedido di : pedido.getItems()) {
                existing.getItems().stream()
                        .filter(ei -> di.getSku().equals(ei.getSku()))
                        .findFirst()
                        .ifPresent(ei -> updateItemFields(ei, di));
            }
            jpaRepository.save(existing);
        } else {
            jpaRepository.save(toEntity(pedido));
        }
    }

    private void updateItemFields(PedidoItemEntity ei, ItemPedido di) {
        if (di.getReservaId() != null) ei.setReservaId(di.getReservaId());
        if (di.getFreteId() != null) {
            ei.setFreteId(di.getFreteId());
            ei.setValorFrete(di.getValorFrete());
            ei.setPrazoEntrega(di.getPrazoEntrega());
        }
    }

    @Override
    public Optional<Pedido> buscarPorId(String pedidoId) {
        return jpaRepository.findById(pedidoId).map(this::toDomain);
    }

    @Override
    public List<Pedido> buscarTodos() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(String pedidoId) {
        return jpaRepository.existsById(pedidoId);
    }

    private PedidoItemEntity toItemEntity(PedidoEntity entity, ItemPedido item) {
        return new PedidoItemEntity(
                entity,
                item.getSku(),
                item.getQuantidade(),
                item.getValorUnitario(),
                item.getReservaId(),
                item.getFreteId(),
                item.getValorFrete(),
                item.getPrazoEntrega());
    }

    private PedidoEntity toEntity(Pedido pedido) {
        PedidoEntity entity = new PedidoEntity(
                pedido.getPedidoId(),
                pedido.getCepDestino(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getTransacaoId(),
                pedido.getMensagemErro());

        for (ItemPedido item : pedido.getItems()) {
            entity.addItem(toItemEntity(entity, item));
        }

        return entity;
    }

    private Pedido toDomain(PedidoEntity entity) {
        Pedido pedido = Pedido.criar(
                entity.getPedidoId(),
                entity.getCepDestino());

        for (PedidoItemEntity itemEntity : entity.getItems()) {
            ItemPedido item = ItemPedido.criar(
                    itemEntity.getSku(),
                    itemEntity.getQuantidade(),
                    itemEntity.getValorUnitario());

            if (itemEntity.getReservaId() != null) {
                item.reservarEstoque(itemEntity.getReservaId());
            }
            if (itemEntity.getFreteId() != null) {
                item.calcularFrete(itemEntity.getFreteId(), itemEntity.getValorFrete(), itemEntity.getPrazoEntrega());
            }

            pedido.adicionarItem(item);
        }

        pedido.restaurar(entity.getStatus(), entity.getMensagemErro(), entity.getTransacaoId());

        return pedido;
    }
}
