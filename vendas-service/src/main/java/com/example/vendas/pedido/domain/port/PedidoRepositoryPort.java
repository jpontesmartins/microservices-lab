package com.example.vendas.pedido.domain.port;

import com.example.vendas.pedido.domain.model.Pedido;

import java.util.Optional;

/**
 * Porta de saida para persistencia de pedidos.
 * Define o contrato que a camada de infraestrutura deve implementar.
 */
public interface PedidoRepositoryPort {

    void salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(String pedidoId);

    boolean existsById(String pedidoId);
}
