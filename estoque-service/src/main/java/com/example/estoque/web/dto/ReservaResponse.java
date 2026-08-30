package com.example.estoque.web.dto;

public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}
