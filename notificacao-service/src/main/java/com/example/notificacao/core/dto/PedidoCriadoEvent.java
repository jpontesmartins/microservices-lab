package com.example.notificacao.core.dto;

import java.util.List;

public record PedidoCriadoEvent(
        String pedidoId,
        List<ItemEvent> items,
        double valorTotal,
        double valorFreteTotal,
        String cepDestino
) {
    public record ItemEvent(String sku, int quantidade, double valorUnitario, double subtotal) {
    }
}
