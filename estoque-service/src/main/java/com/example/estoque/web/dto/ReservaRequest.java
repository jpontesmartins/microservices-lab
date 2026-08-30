package com.example.estoque.web.dto;

public record ReservaRequest(String pedidoId, String sku, int quantidade) {
}
