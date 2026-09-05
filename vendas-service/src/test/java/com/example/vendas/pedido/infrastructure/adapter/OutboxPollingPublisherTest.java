package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.entities.OutboxEventoEntity;
import com.example.vendas.pedido.infrastructure.dto.PedidoCriadoEvent;
import com.example.vendas.pedido.infrastructure.repository.OutboxEventoJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    private OutboxEventoJpaRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate;

    private OutboxPollingPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        publisher = new OutboxPollingPublisher(outboxRepository, kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("nao deve fazer nada quando nao ha eventos pendentes")
    void naoDeveFazerNadaQuandoNaoHaEventosPendentes() {
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        publisher.publicarEventosPendentes();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("deve enviar eventos pendentes para o Kafka")
    void deveEnviarEventosPendentesParaKafka() throws Exception {
        PedidoCriadoEvent event = new PedidoCriadoEvent(
                "pedido-001",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 100.0, 200.0)),
                200.0, 0.0, "01310-100");

        String payload = objectMapper.writeValueAsString(event);

        OutboxEventoEntity entity = new OutboxEventoEntity(
                "pedido-001", "PEDIDO_CRIADO", payload, Instant.now());

        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(entity));

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("pedido-criado", 0), 0, 0, 0L, 0, 0);
        SendResult<String, PedidoCriadoEvent> sendResult = new SendResult<>(
                new org.apache.kafka.clients.producer.ProducerRecord<>("pedido-criado", "pedido-001", event),
                metadata);

        CompletableFuture<SendResult<String, PedidoCriadoEvent>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("pedido-criado"), eq("pedido-001"), any(PedidoCriadoEvent.class)))
                .thenReturn(future);

        publisher.publicarEventosPendentes();

        verify(kafkaTemplate).send(eq("pedido-criado"), eq("pedido-001"), any(PedidoCriadoEvent.class));
    }

    @Test
    @DisplayName("deve tratar erro de serializacao sem propagar excecao")
    void deveTratarErroDeSerializacaoSemPropagarExcecao() {
        OutboxEventoEntity entityInvalido = new OutboxEventoEntity(
                "pedido-002", "PEDIDO_CRIADO", "INVALID JSON", Instant.now());

        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(entityInvalido));

        publisher.publicarEventosPendentes();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("deve processar multiplos eventos em lote")
    void deveProcessarMultiplosEventosEmLote() throws Exception {
        PedidoCriadoEvent event1 = new PedidoCriadoEvent(
                "pedido-001",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-ABC", 2, 100.0, 200.0)),
                200.0, 0.0, "01310-100");
        PedidoCriadoEvent event2 = new PedidoCriadoEvent(
                "pedido-002",
                List.of(new PedidoCriadoEvent.ItemEvent("SKU-DEF", 1, 50.0, 50.0)),
                50.0, 0.0, "02010-200");

        OutboxEventoEntity entity1 = new OutboxEventoEntity(
                "pedido-001", "PEDIDO_CRIADO", objectMapper.writeValueAsString(event1), Instant.now());
        OutboxEventoEntity entity2 = new OutboxEventoEntity(
                "pedido-002", "PEDIDO_CRIADO", objectMapper.writeValueAsString(event2), Instant.now());

        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(entity1, entity2));

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("pedido-criado", 0), 0, 0, 0L, 0, 0);
        SendResult<String, PedidoCriadoEvent> sendResult = new SendResult<>(
                new org.apache.kafka.clients.producer.ProducerRecord<>("pedido-criado", "x", null),
                metadata);
        CompletableFuture<SendResult<String, PedidoCriadoEvent>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(any(), any(), any(PedidoCriadoEvent.class)))
                .thenReturn(future);

        publisher.publicarEventosPendentes();

        verify(kafkaTemplate).send(eq("pedido-criado"), eq("pedido-001"), any(PedidoCriadoEvent.class));
        verify(kafkaTemplate).send(eq("pedido-criado"), eq("pedido-002"), any(PedidoCriadoEvent.class));
    }
}
