package com.example.vendas.core.dto;

/**
 * Resposta com os dados completos de um pedido.
 *
 * @param pedidoId    identificador do pedido
 * @param status      status atual do pedido (CRIADO, ESTOQUE_RESERVADO, FRETE_CALCULADO, PAGO, FALHA_TRANSITÓRIA, etc.)
 * @param sku         código do produto
 * @param quantidade  quantidade do produto
 * @param valor       valor unitário do produto
 * @param valorFrete  valor do frete calculado
 * @param prazoEntrega prazo estimado de entrega
 * @param reservaId   identificador da reserva de estoque
 * @param freteId     identificador do frete
 * @param transacaoId identificador da transação de pagamento
 * @param criadoEm    data/hora de criação do pedido (ISO-8601)
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

