package com.example.vendas.pedido.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class PedidoEntity {

    @Id
    @Column(name = "pedido_id")
    private String pedidoId;

    @Column(name = "cep_destino", nullable = false)
    private String cepDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.example.vendas.pedido.domain.model.StatusPedido status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "transacao_id")
    private String transacaoId;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoItemEntity> items = new ArrayList<>();

    protected PedidoEntity() {
    }

    public PedidoEntity(String pedidoId, String cepDestino,
            com.example.vendas.pedido.domain.model.StatusPedido status, Instant criadoEm,
            String transacaoId, String mensagemErro) {
        this.pedidoId = pedidoId;
        this.cepDestino = cepDestino;
        this.status = status;
        this.criadoEm = criadoEm;
        this.transacaoId = transacaoId;
        this.mensagemErro = mensagemErro;
    }

    public void addItem(PedidoItemEntity item) {
        items.add(item);
        item.setPedido(this);
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public String getCepDestino() {
        return cepDestino;
    }

    public com.example.vendas.pedido.domain.model.StatusPedido getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getTransacaoId() {
        return transacaoId;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public List<PedidoItemEntity> getItems() {
        return items;
    }
}
