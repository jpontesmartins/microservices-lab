package com.example.vendas.integration.dto;

/**
 * Request para calculo de frete no frete-service.
 *
 * @param pedidoId   identificador do pedido
 * @param sku        codigo do produto
 * @param quantidade quantidade do produto
 * @param cepDestino CEP de destino para o calculo
 */
public record FreteRequest(String pedidoId, String sku, int quantidade, String cepDestino) {
}
