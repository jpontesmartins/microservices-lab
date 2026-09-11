package com.example.estoque.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "itens_estoque")
public class ItemEstoqueEntity {

    @Id
    @Column(name = "sku")
    private String sku;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "imagem")
    private String imagem;

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    protected ItemEstoqueEntity() {
    }

    public ItemEstoqueEntity(String sku, String descricao, String imagem, BigDecimal valor, int quantidade) {
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

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }
}
