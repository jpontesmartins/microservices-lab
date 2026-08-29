package com.example.vendas.pedido.infrastructure.dto;

public record PagamentoRequest(String pedidoId, double valor) {
}
