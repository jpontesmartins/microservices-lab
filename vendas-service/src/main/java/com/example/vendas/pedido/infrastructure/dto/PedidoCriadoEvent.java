package com.example.vendas.pedido.infrastructure.dto;

public record PedidoCriadoEvent(String pedidoId, String sku, int quantidade, double valor, String cepDestino) {
}
