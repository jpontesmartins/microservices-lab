package com.example.vendas.pedido.web.dto;

import java.util.List;

/**
 * Request para criacao de um novo pedido.
 *
 * @param items     lista de itens do pedido
 * @param cepDestino CEP de destino para calculo de frete
 */
public record CriarPedidoRequest(List<ItemPedidoRequest> items, String cepDestino) {
}
