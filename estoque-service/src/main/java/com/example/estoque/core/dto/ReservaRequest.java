package com.example.estoque.core.dto;

public record ReservaRequest(String pedidoId, String sku, int quantidade) {
}

