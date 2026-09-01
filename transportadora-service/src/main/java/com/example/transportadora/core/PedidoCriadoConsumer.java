package com.example.transportadora.core;

import com.example.transportadora.core.dto.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PedidoCriadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoConsumer.class);

    @KafkaListener(topics = "pedido-criado", groupId = "transportadora-group")
    public void onPedidoCriado(PedidoCriadoEvent event) {
        String skus = event.items().stream()
                .map(i -> i.sku() + " x" + i.quantidade())
                .collect(Collectors.joining(", "));
        log.info("Pedido sendo processado pela transportadora (pedidoId={}, itens={}, valorTotal={}, cepDestino={})",
                event.pedidoId(), skus, event.valorTotal(), event.cepDestino());
    }
}
