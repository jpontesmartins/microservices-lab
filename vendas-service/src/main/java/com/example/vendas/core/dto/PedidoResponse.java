package com.example.vendas.core.dto;

public record PedidoResponse(
        String pedidoId,
        String status,
        String sku,
        int quantidade,
        double valor,
        String reservaId,
        String transacaoId,
        String criadoEm
) {
}

