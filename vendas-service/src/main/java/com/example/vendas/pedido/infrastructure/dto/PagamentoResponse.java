package com.example.vendas.pedido.infrastructure.dto;

public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
}
