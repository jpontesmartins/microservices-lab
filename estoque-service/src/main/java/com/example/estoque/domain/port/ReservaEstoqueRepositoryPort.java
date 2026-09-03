package com.example.estoque.domain.port;

import com.example.estoque.domain.model.ReservaEstoque;

import java.util.Optional;

public interface ReservaEstoqueRepositoryPort {

    ReservaEstoque salvar(ReservaEstoque reserva);

    Optional<ReservaEstoque> buscarPorId(String id);

    void removerPorId(String id);

    boolean existsByPedidoIdAndSku(String pedidoId, String sku);

    Optional<ReservaEstoque> buscarPorPedidoIdESku(String pedidoId, String sku);
}
