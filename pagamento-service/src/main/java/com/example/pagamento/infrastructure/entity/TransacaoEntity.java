package com.example.pagamento.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transacoes")
public class TransacaoEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private String pedidoId;

    @Column(name = "valor", nullable = false)
    private double valor;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected TransacaoEntity() {
    }

    public TransacaoEntity(String id, String pedidoId, double valor, String status, Instant criadoEm) {
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
