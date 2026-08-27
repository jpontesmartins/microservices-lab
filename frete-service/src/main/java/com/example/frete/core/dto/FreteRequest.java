package com.example.frete.core.dto;

/**
 * Request para cálculo de frete.
 *
 * @param pedidoId   identificador do pedido
 * @param sku        código do produto
 * @param quantidade quantidade do produto
 * @param cepDestino CEP de destino para o cálculo
 */
public record FreteRequest(String pedidoId, String sku, int quantidade, String cepDestino) {
}
