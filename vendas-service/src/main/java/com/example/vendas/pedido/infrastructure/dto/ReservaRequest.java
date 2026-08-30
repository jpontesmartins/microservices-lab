package com.example.vendas.pedido.infrastructure.dto;

public record ReservaRequest(String pedidoId, String sku, int quantidade) {
}
