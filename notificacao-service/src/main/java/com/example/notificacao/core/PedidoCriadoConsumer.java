package com.example.notificacao.core;

import com.example.notificacao.core.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PedidoCriadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoConsumer.class);

    @KafkaListener(topics = "pedido-criado", groupId = "notificacao-group")
    public void onPedidoCriado(PedidoCriadoEvent event) {
        String skus = event.items().stream()
                .map(i -> i.sku() + " x" + i.quantidade())
                .collect(Collectors.joining(", "));
        log.info("Notificacao enviada ao usuario (pedidoId={}, itens={}, valorTotal={})",
                event.pedidoId(), skus, event.valorTotal());
    }
}
