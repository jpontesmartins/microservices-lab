package com.example.vendas.pedido.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelo de dominio que representa um Pedido no bounded context de vendas.
 * Encapsula as regras de negocio e estado do pedido ao longo do fluxo saga.
 * Um pedido contem uma lista de itens, cada um com suas integracoes de estoque e frete.
 */
public class Pedido {

    private String pedidoId;
    private String cepDestino;
    private StatusPedido status;
    private Instant criadoEm;
    private String transacaoId;
    private final List<ItemPedido> items = new ArrayList<>();

    private Pedido() {
    }

    public static Pedido criar(String pedidoId, String cepDestino) {
        Pedido pedido = new Pedido();
        pedido.pedidoId = pedidoId;
        pedido.cepDestino = cepDestino;
        pedido.status = StatusPedido.CRIADO;
        pedido.criadoEm = Instant.now();
        return pedido;
    }

    public void adicionarItem(ItemPedido item) {
        this.items.add(item);
    }

    public void reservarEstoque(String reservaId) {
        this.status = StatusPedido.ESTOQUE_RESERVADO;
    }

    public void calcularFrete() {
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
        double subtotal = items.stream().mapToDouble(ItemPedido::getSubtotal).sum();
        double freteTotal = calcularValorFreteTotal();
        return subtotal + freteTotal;
    }

    public double calcularValorFreteTotal() {
        return items.stream().mapToDouble(ItemPedido::getValorFrete).sum();
    }

    public String getPedidoId() {
        return pedidoId;
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

    public String getTransacaoId() {
        return transacaoId;
    }

    public List<ItemPedido> getItems() {
        return Collections.unmodifiableList(items);
    }
}
