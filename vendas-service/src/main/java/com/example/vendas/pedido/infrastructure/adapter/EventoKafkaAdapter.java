package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.infrastructure.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventoKafkaAdapter implements EventoPublicacaoPort {

    private static final Logger log = LoggerFactory.getLogger(EventoKafkaAdapter.class);
    private static final String TOPIC = "pedido-criado";
    private final KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate;

    public EventoKafkaAdapter(KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publicarPedidoCriado(Pedido pedido) {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                pedido.getPedidoId(),
                pedido.getSku(),
                pedido.getQuantidade(),
                pedido.calcularValorTotal(),
                pedido.getCepDestino()
        );
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
