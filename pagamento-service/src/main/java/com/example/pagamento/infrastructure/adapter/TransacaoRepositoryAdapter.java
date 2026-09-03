package com.example.pagamento.infrastructure.adapter;

import com.example.pagamento.domain.model.Transacao;
import com.example.pagamento.domain.port.TransacaoRepositoryPort;
import com.example.pagamento.infrastructure.entity.TransacaoEntity;
import com.example.pagamento.infrastructure.repository.TransacaoJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TransacaoRepositoryAdapter implements TransacaoRepositoryPort {

    private final TransacaoJpaRepository jpaRepository;

    public TransacaoRepositoryAdapter(TransacaoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Transacao> buscarPorPedidoId(String pedidoId) {
        return jpaRepository.findByPedidoId(pedidoId)
                .map(e -> new Transacao(e.getId(), e.getPedidoId(), e.getValor(),
                        e.getStatus(), e.getCriadoEm()));
    }

    @Override
    public void salvar(Transacao transacao) {
        try {
            TransacaoEntity entity = new TransacaoEntity(
                    transacao.getId(),
                    transacao.getPedidoId(),
                    transacao.getValor(),
                    transacao.getStatus(),
                    transacao.getCriadoEm());
            jpaRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            Optional<TransacaoEntity> existente = jpaRepository.findByPedidoId(transacao.getPedidoId());
            if (existente.isPresent()) {
                return;
            }
            throw e;
        }
    }
}
