package com.example.vendas.integration.dto;

/**
 * Resposta do calculo de frete no frete-service.
 *
 * @param freteId     identificador do frete
 * @param status      status do calculo (CALCULADO, FALHA_TRANSATORIA, etc.)
 * @param pedidoId    identificador do pedido
 * @param valorFrete  valor calculado do frete
 * @param prazoEntrega prazo estimado de entrega
 */
public record FreteResponse(String freteId, String status, String pedidoId, double valorFrete, String prazoEntrega) {
}
