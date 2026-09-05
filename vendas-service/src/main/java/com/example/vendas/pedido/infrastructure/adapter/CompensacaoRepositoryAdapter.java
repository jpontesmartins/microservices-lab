package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CompensacaoRepositoryAdapter implements CompensacaoRepositoryPort {

    private final CompensacaoPendenteJpaRepository jpaRepository;

    public CompensacaoRepositoryAdapter(CompensacaoPendenteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarCompensacao(String pedidoId, TipoCompensacao tipo, String refId, int maxAttempts) {
        CompensacaoPendenteEntity entity = new CompensacaoPendenteEntity(pedidoId, tipo, refId, maxAttempts);
        jpaRepository.save(entity);
    }

    @Override
    public List<CompensacaoPendente> buscarPendentesParaProcessar() {
        return jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void marcarEnviado(List<Long> ids) {
        // Nao utilizado — o polling publisher usa save() diretamente
    }

    @Override
    public void marcarFalha(List<Long> ids) {
        // Nao utilizado — o polling publisher usa save() diretamente
    }

    @Override
    public long contarPendentes() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE).size();
    }

    private CompensacaoPendente toDomain(CompensacaoPendenteEntity entity) {
        return new CompensacaoPendente(
                entity.getId(),
                entity.getPedidoId(),
                entity.getTipo(),
                entity.getRefId(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getMaxAttempts(),
                entity.getLastError()
        );
    }
}
