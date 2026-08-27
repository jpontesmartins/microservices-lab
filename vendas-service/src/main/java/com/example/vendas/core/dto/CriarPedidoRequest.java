package com.example.vendas.core.dto;

/**
 * Request para criação de um novo pedido.
 *
 * @param sku        código do produto
 * @param quantidade quantidade do produto
 * @param valor      valor unitário do produto
 * @param cepDestino CEP de destino para cálculo de frete
 */
public record CriarPedidoRequest(String sku, int quantidade, double valor, String cepDestino) {
}

