package com.example.transportadora.core.dto;

/**
 * Evento publicado pelo vendas-service quando um pedido é criado e pago.
 *
 * @param pedidoId    identificador do pedido
 * @param sku         código do produto
 * @param quantidade  quantidade do produto
 * @param valor       valor total do pedido (produto + frete)
 * @param cepDestino  CEP de destino para entrega
 */
public record PedidoCriadoEvent(
        String pedidoId,
        String sku,
        int quantidade,
        double valor,
        String cepDestino
) {
}
