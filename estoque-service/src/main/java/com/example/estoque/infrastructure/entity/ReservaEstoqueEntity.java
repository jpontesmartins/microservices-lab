package com.example.estoque.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservas_estoque")
public class ReservaEstoqueEntity {

    @Id
    @Column(name = "id")
    private String id;

    @ManyToOne
    @JoinColumn(name = "sku", nullable = false)
    private ItemEstoqueEntity item;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "pedido_id", nullable = false)
    private String pedidoId;

    protected ReservaEstoqueEntity() {
    }

    public ReservaEstoqueEntity(String id, ItemEstoqueEntity item, int quantidade, String pedidoId) {
        this.id = id;
        this.item = item;
        this.quantidade = quantidade;
        this.pedidoId = pedidoId;
    }

    public String getId() {
        return id;
    }

    public ItemEstoqueEntity getItem() {
        return item;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public String getPedidoId() {
        return pedidoId;
    }
}
