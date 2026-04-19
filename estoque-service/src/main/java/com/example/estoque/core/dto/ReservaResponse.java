package com.example.estoque.core.dto;

public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}

