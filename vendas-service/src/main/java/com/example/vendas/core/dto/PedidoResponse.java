package com.example.vendas.core.dto;

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

