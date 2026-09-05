package com.example.vendas.pedido.entities;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "compensacoes_pendentes")
public class CompensacaoPendenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private String pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private TipoCompensacao tipo;

    @Column(name = "ref_id", nullable = false)
    private String refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private StatusCompensacao status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompensacaoPendenteEntity() {
    }

    public CompensacaoPendenteEntity(String pedidoId, TipoCompensacao tipo, String refId, int maxAttempts) {
        this.pedidoId = pedidoId;
        this.tipo = tipo;
        this.refId = refId;
        this.status = StatusCompensacao.PENDENTE;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.lastError = null;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public TipoCompensacao getTipo() {
        return tipo;
    }

    public String getRefId() {
        return refId;
    }

    public StatusCompensacao getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void incrementarAttempts(String error) {
        this.attempts++;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void marcarEnviado() {
        this.status = StatusCompensacao.ENVIADO;
        this.updatedAt = Instant.now();
    }

    public void marcarFalha() {
        this.status = StatusCompensacao.FALHA;
        this.updatedAt = Instant.now();
    }
}
