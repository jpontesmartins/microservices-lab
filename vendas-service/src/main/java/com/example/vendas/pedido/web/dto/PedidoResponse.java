package com.example.vendas.pedido.web.dto;

/**
 * Resposta com os dados completos de um pedido.
 *
 * @param pedidoId    identificador do pedido
 * @param status      status atual do pedido
 * @param sku         codigo do produto
 * @param quantidade  quantidade do produto
 * @param valor       valor unitario do produto
 * @param valorFrete  valor do frete calculado
 * @param prazoEntrega prazo estimado de entrega
 * @param reservaId   identificador da reserva de estoque
 * @param freteId     identificador do frete
 * @param transacaoId identificador da transacao de pagamento
 * @param criadoEm    data/hora de criacao do pedido (ISO-8601)
 */
public record PedidoResponse(
        String pedidoId,
        String status,
        String sku,
        int quantidade,
        double valor,
        double valorFrete,
        String prazoEntrega,
        String reservaId,
        String freteId,
        String transacaoId,
        String criadoEm
) {
}
