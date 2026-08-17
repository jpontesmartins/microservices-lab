package com.example.vendas.integration.dto;

/**
 * Request para processamento de pagamento no pagamento-service.
 *
 * @param pedidoId identificador do pedido
 * @param valor    valor total a ser pago
 */
public record PagamentoRequest(String pedidoId, double valor) {
}

