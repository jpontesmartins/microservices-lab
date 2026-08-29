package com.example.vendas.pedido.web.dto;

/**
 * Request para criacao de um item dentro de um pedido.
 *
 * @param sku        codigo do produto
 * @param quantidade quantidade do produto
 * @param valor      valor unitario do produto
 */
public record ItemPedidoRequest(String sku, int quantidade, double valor) {
}
