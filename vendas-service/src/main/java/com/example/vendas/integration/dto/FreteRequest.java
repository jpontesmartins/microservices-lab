package com.example.vendas.integration.dto;

public record FreteRequest(String pedidoId, String sku, int quantidade, String cepDestino) {
}
