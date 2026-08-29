package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.ItemPedido;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.model.StatusPedido;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.entities.PedidoEntity;
import com.example.vendas.pedido.entities.PedidoItemEntity;
import com.example.vendas.pedido.infrastructure.repository.PedidoJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PedidoRepositoryAdapter implements PedidoRepositoryPort {

    private final PedidoJpaRepository jpaRepository;

    public PedidoRepositoryAdapter(PedidoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void salvar(Pedido pedido) {
        PedidoEntity entity = toEntity(pedido);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Pedido> buscarPorId(String pedidoId) {
        return jpaRepository.findById(pedidoId).map(this::toDomain);
    }

    private PedidoEntity toEntity(Pedido pedido) {
        PedidoEntity entity = new PedidoEntity(
                pedido.getPedidoId(),
                pedido.getCepDestino(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getTransacaoId());

        for (ItemPedido item : pedido.getItems()) {
            PedidoItemEntity itemEntity = new PedidoItemEntity(
                    entity,
                    item.getSku(),
                    item.getQuantidade(),
                    item.getValorUnitario(),
                    item.getReservaId(),
                    item.getFreteId(),
                    item.getValorFrete(),
                    item.getPrazoEntrega());
            entity.addItem(itemEntity);
        }

        return entity;
    }

    private Pedido toDomain(PedidoEntity entity) {
        Pedido pedido = Pedido.criar(
                entity.getPedidoId(),
                entity.getCepDestino());

        if (entity.getStatus() != StatusPedido.CRIADO) {
            pedido.marcarFalha(entity.getStatus());
        }

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

        if (entity.getTransacaoId() != null) {
            pedido.confirmarPagamento(entity.getTransacaoId());
        }

        return pedido;
    }
}
