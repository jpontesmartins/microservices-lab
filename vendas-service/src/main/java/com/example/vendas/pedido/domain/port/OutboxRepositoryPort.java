package com.example.vendas.pedido.domain.port;

import com.example.vendas.pedido.domain.model.Pedido;

/**
 * Porta de saida para persistencia de eventos no outbox.
 * Garante que o evento seja salvo na mesma transacao do negocio.
 */
public interface OutboxRepositoryPort {

    void salvarEventoPedidoCriado(Pedido pedido, String payload);
}
