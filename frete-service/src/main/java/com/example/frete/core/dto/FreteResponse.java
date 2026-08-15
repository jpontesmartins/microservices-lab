package com.example.frete.core.dto;

public record FreteResponse(String freteId, String status, String pedidoId, double valorFrete, String prazoEntrega) {
}
