package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.ItemPedido;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.OutboxRepositoryPort;
import com.example.vendas.pedido.infrastructure.dto.PedidoCriadoEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventoKafkaAdapter implements EventoPublicacaoPort {

    private static final Logger log = LoggerFactory.getLogger(EventoKafkaAdapter.class);

    private final OutboxRepositoryPort outboxRepository;
    private final ObjectMapper objectMapper;

    public EventoKafkaAdapter(OutboxRepositoryPort outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicarPedidoCriado(Pedido pedido) {
        List<PedidoCriadoEvent.ItemEvent> itemEvents = pedido.getItems().stream()
                .map(item -> new PedidoCriadoEvent.ItemEvent(
                        item.getSku(), item.getQuantidade(), item.getValorUnitario(), item.getSubtotal()))
                .toList();

        PedidoCriadoEvent event = new PedidoCriadoEvent(
                pedido.getPedidoId(),
                itemEvents,
                pedido.calcularValorTotal(),
                pedido.calcularValorFreteTotal(),
                pedido.getCepDestino()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.salvarEventoPedidoCriado(pedido, payload);
            log.info("Evento PedidoCriado salvo no outbox (pedidoId={})", pedido.getPedidoId());
        } catch (Exception e) {
            log.error("Falha ao serializar e salvar evento no outbox (pedidoId={})", pedido.getPedidoId(), e);
            throw new RuntimeException("Falha ao salvar evento no outbox", e);
        }
    }
}
