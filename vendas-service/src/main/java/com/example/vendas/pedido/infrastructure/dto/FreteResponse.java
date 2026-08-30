package com.example.vendas.pedido.infrastructure.dto;

public record FreteResponse(String freteId, String status, String pedidoId, double valorFrete, String prazoEntrega) {
}
