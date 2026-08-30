package com.example.estoque.domain.model;

public class ReservaEstoque {

    private final String id;
    private final String sku;
    private final int quantidade;
    private final String pedidoId;

    public ReservaEstoque(String id, String sku, int quantidade, String pedidoId) {
        this.id = id;
        this.sku = sku;
        this.quantidade = quantidade;
        this.pedidoId = pedidoId;
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public String getPedidoId() {
        return pedidoId;
    }
}
