package com.example.vendas.pedido.domain.model;

/**
 * Modelo de dominio que representa um item dentro de um Pedido.
 * Cada item contem informacoes sobre o produto, quantidade, valores e integracoes.
 */
public class ItemPedido {

    private String sku;
    private int quantidade;
    private double valorUnitario;
    private String reservaId;
    private String freteId;
    private double valorFrete;
    private String prazoEntrega;

    private ItemPedido() {
    }

    public static ItemPedido criar(String sku, int quantidade, double valorUnitario) {
        ItemPedido item = new ItemPedido();
        item.sku = sku;
        item.quantidade = quantidade;
        item.valorUnitario = valorUnitario;
        return item;
    }

    public double getSubtotal() {
        return this.valorUnitario * this.quantidade;
    }

    public void reservarEstoque(String reservaId) {
        this.reservaId = reservaId;
    }

    public void calcularFrete(String freteId, double valorFrete, String prazoEntrega) {
        this.freteId = freteId;
        this.valorFrete = valorFrete;
        this.prazoEntrega = prazoEntrega;
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
