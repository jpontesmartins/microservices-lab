package com.example.vendas.integration.dto;

/**
 * Resposta da reserva de estoque no estoque-service.
 *
 * @param reservaId  identificador da reserva
 * @param status     status da reserva (RESERVADO, FALHA_TRANSATORIA, etc.)
 * @param sku        codigo do produto
 * @param quantidade quantidade reservada
 * @param pedidoId   identificador do pedido
 */
public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}

