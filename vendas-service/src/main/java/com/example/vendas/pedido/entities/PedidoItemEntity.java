package com.example.vendas.pedido.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido_itens")
public class PedidoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoEntity pedido;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "valor_unitario", nullable = false)
    private double valorUnitario;

    @Column(name = "reserva_id")
    private String reservaId;

    @Column(name = "frete_id")
    private String freteId;

    @Column(name = "valor_frete")
    private double valorFrete;

    @Column(name = "prazo_entrega")
    private String prazoEntrega;

    protected PedidoItemEntity() {
    }

    public PedidoItemEntity(PedidoEntity pedido, String sku, int quantidade, double valorUnitario,
            String reservaId, String freteId, double valorFrete, String prazoEntrega) {
        this.pedido = pedido;
        this.sku = sku;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
        this.reservaId = reservaId;
        this.freteId = freteId;
        this.valorFrete = valorFrete;
        this.prazoEntrega = prazoEntrega;
    }

    public Long getId() {
        return id;
    }

    public PedidoEntity getPedido() {
        return pedido;
    }

    public void setPedido(PedidoEntity pedido) {
        this.pedido = pedido;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public double getValorUnitario() {
        return valorUnitario;
    }

    public String getReservaId() {
        return reservaId;
    }

    public String getFreteId() {
        return freteId;
    }

    public double getValorFrete() {
        return valorFrete;
    }

    public String getPrazoEntrega() {
        return prazoEntrega;
    }
}
