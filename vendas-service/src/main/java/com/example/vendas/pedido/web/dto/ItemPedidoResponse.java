package com.example.vendas.pedido.web.dto;

/**
 * Resposta com os dados de um item dentro de um pedido.
 *
 * @param sku         codigo do produto
 * @param quantidade  quantidade do produto
 * @param valorUnitario valor unitario do produto
 * @param subtotal    subtotal do item (quantidade * valor unitario)
 * @param valorFrete  valor do frete calculado para o item
 * @param prazoEntrega prazo estimado de entrega para o item
 * @param reservaId   identificador da reserva de estoque do item
 * @param freteId     identificador do frete do item
 */
public record ItemPedidoResponse(
        String sku,
        int quantidade,
        double valorUnitario,
        double subtotal,
        double valorFrete,
        String prazoEntrega,
        String reservaId,
        String freteId
) {
}
