package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.entities.OutboxEventoEntity;
import com.example.vendas.pedido.infrastructure.dto.PedidoCriadoEvent;
import com.example.vendas.pedido.infrastructure.repository.OutboxEventoJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPollingPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingPublisher.class);
    private static final String TOPIC = "pedido-criado";

    private final OutboxEventoJpaRepository outboxRepository;
    private final KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPollingPublisher(OutboxEventoJpaRepository outboxRepository,
            KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.polling-interval:5000}", initialDelayString = "${outbox.initial-delay:2000}")
    @Transactional(readOnly = true)
    public void publicarEventosPendentes() {
        List<OutboxEventoEntity> eventos = outboxRepository
                .findByPublishedFalseOrderByCreatedAtAsc();

        if (eventos.isEmpty()) {
            return;
        }

        log.info("Polling outbox: {} eventos pendentes encontrados", eventos.size());

        for (OutboxEventoEntity evento : eventos) {
            try {
                PedidoCriadoEvent event = objectMapper.readValue(evento.getPayload(), PedidoCriadoEvent.class);

                kafkaTemplate.send(TOPIC, event.pedidoId(), event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Falha ao publicar evento do outbox (id={}, pedidoId={})",
                                        evento.getId(), evento.getAggregateId(), ex);
                            } else {
                                log.info("Evento publicado com sucesso (id={}, pedidoId={}, partition={}, offset={})",
                                        evento.getId(), event.pedidoId(),
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());
                                outboxRepository.markAsPublished(List.of(evento.getId()));
                            }
                        });
            } catch (Exception e) {
                log.error("Falha ao processar evento do outbox (id={}, pedidoId={})",
                        evento.getId(), evento.getAggregateId(), e);
            }
        }
    }
}
