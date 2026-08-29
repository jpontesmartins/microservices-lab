package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PedidoRepositoryAdapter implements PedidoRepositoryPort {

    private final ConcurrentHashMap<String, Pedido> pedidos = new ConcurrentHashMap<>();

    @Override
    public void salvar(Pedido pedido) {
        pedidos.put(pedido.getPedidoId(), pedido);
    }

    @Override
    public Optional<Pedido> buscarPorId(String pedidoId) {
        return Optional.ofNullable(pedidos.get(pedidoId));
    }
}
