package com.example.vendas.integration.dto;

/**
 * Resposta do processamento de pagamento no pagamento-service.
 *
 * @param transacaoId identificador da transação
 * @param status      status do pagamento (APROVADO, FALHA_TRANSITÓRIA, etc.)
 * @param pedidoId    identificador do pedido
 * @param valor       valor processado
 */
public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
}

