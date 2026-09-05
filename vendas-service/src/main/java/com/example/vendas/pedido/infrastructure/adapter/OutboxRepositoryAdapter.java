package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.OutboxRepositoryPort;
import com.example.vendas.pedido.entities.OutboxEventoEntity;
import com.example.vendas.pedido.infrastructure.repository.OutboxEventoJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    private static final String EVENT_TYPE_PEDIDO_CRIADO = "PEDIDO_CRIADO";

    private final OutboxEventoJpaRepository jpaRepository;

    public OutboxRepositoryAdapter(OutboxEventoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void salvarEventoPedidoCriado(Pedido pedido, String payload) {
        OutboxEventoEntity entity = new OutboxEventoEntity(
                pedido.getPedidoId(),
                EVENT_TYPE_PEDIDO_CRIADO,
                payload,
                Instant.now());
        jpaRepository.save(entity);
    }
}
