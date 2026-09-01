package com.example.vendas.pedido.web.dto;

import java.util.List;

/**
 * Resposta com os dados completos de um pedido.
 *
 * @param pedidoId        identificador do pedido
 * @param status          status atual do pedido
 * @param items           lista de itens do pedido
 * @param valorTotal      valor total do pedido (soma dos subtotais + fretes)
 * @param valorFreteTotal valor total do frete (soma dos fretes dos itens)
 * @param transacaoId     identificador da transacao de pagamento
 * @param criadoEm        data/hora de criacao do pedido (ISO-8601)
 */
public record PedidoResponse(
        String pedidoId,
        String status,
        List<ItemPedidoResponse> items,
        double valorTotal,
        double valorFreteTotal,
        String transacaoId,
        String criadoEm,
        String mensagemErro
) {
}
