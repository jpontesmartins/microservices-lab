package com.example.estoque.core.dto;

/**
 * Request para criação de uma reserva de estoque.
 *
 * @param pedidoId  identificador do pedido
 * @param sku       código do produto
 * @param quantidade quantidade a reservar
 */
public record ReservaRequest(String pedidoId, String sku, int quantidade) {
}

