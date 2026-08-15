package com.example.vendas.integration.dto;

public record FreteResponse(String freteId, String status, String pedidoId, double valorFrete, String prazoEntrega) {
}
