package com.example.vendas.pedido.infrastructure.dto;

public record ReservaResponse(String reservaId, String status, String sku, int quantidade, String pedidoId) {
}
