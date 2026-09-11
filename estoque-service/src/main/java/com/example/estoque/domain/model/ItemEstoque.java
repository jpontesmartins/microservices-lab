package com.example.estoque.domain.model;

import java.math.BigDecimal;

public class ItemEstoque {

    private final String sku;
    private final String descricao;
    private final String imagem;
    private final BigDecimal valor;
    private int quantidade;

    public ItemEstoque(String sku, String descricao, String imagem, BigDecimal valor, int quantidade) {
        this.sku = sku;
        this.descricao = descricao;
        this.imagem = imagem;
        this.valor = valor;
        this.quantidade = quantidade;
    }

    public String getSku() {
        return sku;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getImagem() {
        return imagem;
    }

    public BigDecimal getValor() {
        return valor;
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
