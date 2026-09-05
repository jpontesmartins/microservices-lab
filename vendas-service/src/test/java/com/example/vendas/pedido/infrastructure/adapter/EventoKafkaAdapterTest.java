package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.ItemPedido;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.port.OutboxRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoKafkaAdapterTest {

    @Mock
    private OutboxRepositoryPort outboxRepository;

    @InjectMocks
    private EventoKafkaAdapter adapter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        adapter = new EventoKafkaAdapter(outboxRepository, objectMapper);

        pedido = Pedido.criar("pedido-001", "01310-100");
        pedido.adicionarItem(ItemPedido.criar("SKU-ABC", 2, 100.0));
        pedido.confirmarPagamento("transacao-001");
    }

    @Test
    @DisplayName("deve salvar evento no outbox em vez de enviar direto ao Kafka")
    void deveSalvarEventoNoOutboxEmVezDeEnviarDiretoAoKafka() {
        adapter.publicarPedidoCriado(pedido);

        verify(outboxRepository).salvarEventoPedidoCriado(eq(pedido), any(String.class));
    }

    @Test
    @DisplayName("deve lancar RuntimeException quando serializacao falha")
    void deveLancarRuntimeExceptionQuandoSerializacaoFalha() {
        OutboxRepositoryPort failingOutbox = new OutboxRepositoryPort() {
            @Override
            public void salvarEventoPedidoCriado(Pedido pedido, String payload) {
                throw new RuntimeException("DB error");
            }
        };

        EventoKafkaAdapter failingAdapter = new EventoKafkaAdapter(failingOutbox, objectMapper);

        assertThatThrownBy(() -> failingAdapter.publicarPedidoCriado(pedido))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha ao salvar evento no outbox");
    }
}
