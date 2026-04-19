package com.example.vendas.integration.dto;

public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}

