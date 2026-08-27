package com.example.transportadora.core;

import com.example.transportadora.core.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer que escuta a topic "pedido-criado" no Kafka.
 * Responsável por processar o envio de pacotes após pagamento confirmado.
 */
@Component
public class PedidoCriadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoConsumer.class);

    /**
     * Escuta mensagens da topic "pedido-criado" e processa o envio do pacote.
     *
     * @param event dados do pedido criado e pago
     */
    @KafkaListener(topics = "pedido-criado", groupId = "transportadora-group")
    public void onPedidoCriado(PedidoCriadoEvent event) {
        log.info("Pedido sendo processado pela transportadora (pedidoId={}, sku={}, cepDestino={})",
                event.pedidoId(), event.sku(), event.cepDestino());
    }
}
