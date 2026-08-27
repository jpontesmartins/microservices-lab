package com.example.frete.core.dto;

/**
 * Resposta do cálculo de frete.
 *
 * @param freteId     identificador do frete
 * @param status      status do cálculo (CALCULADO, FALHA_TRANSITÓRIA, etc.)
 * @param pedidoId    identificador do pedido
 * @param valorFrete  valor calculado do frete
 * @param prazoEntrega prazo estimado de entrega
 */
public record FreteResponse(String freteId, String status, String pedidoId, double valorFrete, String prazoEntrega) {
}
