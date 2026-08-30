package com.example.vendas.pedido.domain.model;

public enum StatusPedido {
    CRIADO,
    ESTOQUE_RESERVADO,
    FRETE_CALCULADO,
    PAGO,
    FALHA_ESTOQUE,
    FALHA_FRETE,
    FALHA_PAGAMENTO,
    FALHA_TRANSITORIA
}
