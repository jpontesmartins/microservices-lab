package com.example.vendas.pedido.web.dto;

/**
 * Request para criacao de um novo pedido.
 *
 * @param sku        codigo do produto
 * @param quantidade quantidade do produto
 * @param valor      valor unitario do produto
 * @param cepDestino CEP de destino para calculo de frete
 */
public record CriarPedidoRequest(String sku, int quantidade, double valor, String cepDestino) {
}
