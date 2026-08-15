package com.example.vendas.core.dto;

public record CriarPedidoRequest(String sku, int quantidade, double valor, String cepDestino) {
}

