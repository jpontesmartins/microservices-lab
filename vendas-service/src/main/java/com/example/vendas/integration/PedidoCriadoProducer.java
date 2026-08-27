package com.example.vendas.integration;

import com.example.vendas.integration.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer responsavel por publicar eventos de pedido criado no Kafka.
 * Utilizado para notificar serviços consumidores (transportadora, notificação)
 * apos um pedido ser criado e pago com sucesso.
 */
@Component
public class PedidoCriadoProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoProducer.class);
    private static final String TOPIC = "pedido-criado";

    private final KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate;

    public PedidoCriadoProducer(KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica um evento de pedido criado na topic "pedido-criado".
     *
     * @param event dados do pedido criado e pago
     */
    public void publish(PedidoCriadoEvent event) {
        log.info("Publicando evento PedidoCriado no Kafka (topic={}, pedidoId={})", TOPIC, event.pedidoId());
        kafkaTemplate.send(TOPIC, event.pedidoId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar evento PedidoCriado (pedidoId={})", event.pedidoId(), ex);
                    } else {
                        log.info("Evento PedidoCriado publicado com sucesso (pedidoId={}, partition={}, offset={})",
                                event.pedidoId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
