package com.example.estoque.core.dto;

/**
 * Resposta da criacao de uma reserva de estoque.
 *
 * @param reservaId  identificador da reserva
 * @param status     status da reserva (RESERVADO, FALHA_TRANSITORIA, etc.)
 * @param sku        codigo do produto
 * @param quantidade quantidade reservada
 * @param pedidoId   identificador do pedido
 */
public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}

