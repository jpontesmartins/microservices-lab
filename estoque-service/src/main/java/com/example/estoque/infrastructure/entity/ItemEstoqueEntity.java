package com.example.estoque.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "itens_estoque")
public class ItemEstoqueEntity {

    @Id
    @Column(name = "sku")
    private String sku;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    protected ItemEstoqueEntity() {
    }

    public ItemEstoqueEntity(String sku, String descricao, int quantidade) {
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

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }
}
