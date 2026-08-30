package com.example.vendas.pedido.domain.port;

import com.example.vendas.pedido.domain.model.Pedido;

/**
 * Porta de saida para publicacao de eventos de dominio.
 * Define o contrato que a camada de infraestrutura deve implementar.
 */
public interface EventoPublicacaoPort {

    void publicarPedidoCriado(Pedido pedido);
}
