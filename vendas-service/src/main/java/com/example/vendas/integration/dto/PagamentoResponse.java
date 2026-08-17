package com.example.vendas.integration.dto;

/**
 * Resposta do processamento de pagamento no pagamento-service.
 *
 * @param transacaoId identificador da transacao
 * @param status      status do pagamento (APROVADO, FALHA_TRANSATORIA, etc.)
 * @param pedidoId    identificador do pedido
 * @param valor       valor processado
 */
public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
}

