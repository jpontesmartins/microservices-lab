package com.example.vendas.integration.dto;

/**
 * Evento publicado quando um pedido é criado e pago.
 * Enviado via Kafka para os serviços consumidores (transportadora, notificação).
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
