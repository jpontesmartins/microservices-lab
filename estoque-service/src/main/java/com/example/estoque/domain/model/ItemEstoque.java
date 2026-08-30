package com.example.estoque.domain.model;

public class ItemEstoque {

    private final String sku;
    private final String descricao;
    private int quantidade;

    public ItemEstoque(String sku, String descricao, int quantidade) {
        this.sku = sku;
        this.descricao = descricao;
        this.quantidade = quantidade;
    }

    public String getSku() {
        return sku;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void decrementar(int q) {
        this.quantidade -= q;
    }

    public void incrementar(int q) {
        this.quantidade += q;
    }
}
