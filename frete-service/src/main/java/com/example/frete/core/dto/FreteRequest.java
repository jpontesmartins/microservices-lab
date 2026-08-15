package com.example.frete.core.dto;

public record FreteRequest(String pedidoId, String sku, int quantidade, String cepDestino) {
}
