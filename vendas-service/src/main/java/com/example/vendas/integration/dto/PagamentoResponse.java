package com.example.vendas.integration.dto;

public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
}

