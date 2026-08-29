package com.example.vendas.pedido.domain.model;

import java.time.Instant;

/**
 * Modelo de dominio que representa um Pedido no bounded context de vendas.
 * Encapsula as regras de negocio e estado do pedido ao longo do fluxo saga.
 */
public class Pedido {

    private String pedidoId;
    private String sku;
    private int quantidade;
    private double valor;
    private String cepDestino;
    private StatusPedido status;
    private Instant criadoEm;
    private String reservaId;
    private String freteId;
    private double valorFrete;
    private String prazoEntrega;
    private String transacaoId;

    private Pedido() {
    }

    public static Pedido criar(String pedidoId, String sku, int quantidade, double valor, String cepDestino) {
        Pedido pedido = new Pedido();
        pedido.pedidoId = pedidoId;
        pedido.sku = sku;
        pedido.quantidade = quantidade;
        pedido.valor = valor;
        pedido.cepDestino = cepDestino;
        pedido.status = StatusPedido.CRIADO;
        pedido.criadoEm = Instant.now();
        return pedido;
    }

    public void reservarEstoque(String reservaId) {
        this.reservaId = reservaId;
        this.status = StatusPedido.ESTOQUE_RESERVADO;
    }

    public void calcularFrete(String freteId, double valorFrete, String prazoEntrega) {
        this.freteId = freteId;
        this.valorFrete = valorFrete;
        this.prazoEntrega = prazoEntrega;
        this.status = StatusPedido.FRETE_CALCULADO;
    }

    public void confirmarPagamento(String transacaoId) {
        this.transacaoId = transacaoId;
        this.status = StatusPedido.PAGO;
    }

    public void marcarFalha(StatusPedido status) {
        this.status = status;
    }

    public double calcularValorTotal() {
        return this.valor + this.valorFrete;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public double getValor() {
        return valor;
    }

    public String getCepDestino() {
        return cepDestino;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
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

    public String getTransacaoId() {
        return transacaoId;
    }
}
