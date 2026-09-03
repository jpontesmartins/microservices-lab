package com.example.pagamento.domain.model;

import java.time.Instant;

public class Transacao {

    private final String id;
    private final String pedidoId;
    private final double valor;
    private final String status;
    private final Instant criadoEm;

    public Transacao(String id, String pedidoId, double valor, String status, Instant criadoEm) {
        this.id = id;
        this.pedidoId = pedidoId;
        this.valor = valor;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    public String getId() {
        return id;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public double getValor() {
        return valor;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
