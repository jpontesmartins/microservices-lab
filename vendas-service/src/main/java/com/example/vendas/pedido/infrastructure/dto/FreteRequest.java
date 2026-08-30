package com.example.vendas.pedido.infrastructure.dto;

public record FreteRequest(String pedidoId, String sku, int quantidade, String cepDestino) {
}
