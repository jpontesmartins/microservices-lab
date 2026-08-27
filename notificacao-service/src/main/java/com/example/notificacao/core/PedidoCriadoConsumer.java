package com.example.notificacao.core;

import com.example.notificacao.core.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer que escuta a topic "pedido-criado" no Kafka.
 * Responsável por enviar notificações ao usuário após pagamento confirmado.
 */
@Component
public class PedidoCriadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoConsumer.class);

    /**
     * Escuta mensagens da topic "pedido-criado" e envia notificação ao usuário.
     *
     * @param event dados do pedido criado e pago
     */
    @KafkaListener(topics = "pedido-criado", groupId = "notificacao-group")
    public void onPedidoCriado(PedidoCriadoEvent event) {
        log.info("Notificação enviada ao usuário (pedidoId={}, sku={}, valor={})",
                event.pedidoId(), event.sku(), event.valor());
    }
}
