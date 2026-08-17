package com.example.vendas.integration.dto;

/**
 * Request para reserva de estoque no estoque-service.
 *
 * @param pedidoId  identificador do pedido
 * @param sku       codigo do produto
 * @param quantidade quantidade a reservar
 */
public record ReservaRequest(String pedidoId, String sku, int quantidade) {
}

