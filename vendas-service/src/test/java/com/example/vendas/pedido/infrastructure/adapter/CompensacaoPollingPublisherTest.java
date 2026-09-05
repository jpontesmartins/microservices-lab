package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import com.example.vendas.pedido.infrastructure.client.EstoqueClient;
import com.example.vendas.pedido.infrastructure.client.FreteClient;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensacaoPollingPublisherTest {

    @Mock
    private CompensacaoPendenteJpaRepository jpaRepository;

    @Mock
    private EstoqueClient estoqueClient;

    @Mock
    private FreteClient freteClient;

    @InjectMocks
    private CompensacaoPollingPublisher publisher;

    private CompensacaoPendenteEntity compensacaoEstoque;
    private CompensacaoPendenteEntity compensacaoFrete;

    @BeforeEach
    void setUp() {
        compensacaoEstoque = new CompensacaoPendenteEntity(
                "pedido-001", TipoCompensacao.ESTOQUE, "reserva-001", 3);
        setEntityId(compensacaoEstoque, 1L);

        compensacaoFrete = new CompensacaoPendenteEntity(
                "pedido-001", TipoCompensacao.FRETE, "frete-001", 3);
        setEntityId(compensacaoFrete, 2L);
    }

    private void setEntityId(CompensacaoPendenteEntity entity, Long id) {
        try {
            var field = CompensacaoPendenteEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("nao deve fazer nada quando nao ha compensacoes pendentes")
    void naoDeveFazerNadaQuandoNaoHaCompensacoesPendentes() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(Collections.emptyList());

        publisher.processarCompensacoesPendentes();

        verify(estoqueClient, never()).cancelarReserva(any());
        verify(freteClient, never()).cancelar(any());
    }

    @Test
    @DisplayName("deve cancelar reserva de estoque com sucesso")
    void deveCancelarReservaDeEstoqueComSucesso() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoEstoque));

        publisher.processarCompensacoesPendentes();

        verify(estoqueClient).cancelarReserva("reserva-001");
        assertThat(compensacaoEstoque.getStatus()).isEqualTo(StatusCompensacao.ENVIADO);
    }

    @Test
    @DisplayName("deve cancelar frete com sucesso")
    void deveCancelarFreteComSucesso() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoFrete));

        publisher.processarCompensacoesPendentes();

        verify(freteClient).cancelar("frete-001");
        assertThat(compensacaoFrete.getStatus()).isEqualTo(StatusCompensacao.ENVIADO);
    }

    @Test
    @DisplayName("deve tratar 404 como ja cancelado (sucesso)")
    void deveTratar404ComoJaCancelado() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoEstoque));

        FeignException notFound = feignExceptionWithStatus(404);
        doThrow(notFound).when(estoqueClient).cancelarReserva("reserva-001");

        publisher.processarCompensacoesPendentes();

        assertThat(compensacaoEstoque.getStatus()).isEqualTo(StatusCompensacao.ENVIADO);
    }

    @Test
    @DisplayName("deve incrementar attempts e marcar FALHA quando maxAttempts atingido")
    void deveMarcarFalhaQuandoMaxAttemptsAtingido() {
        compensacaoEstoque.incrementarAttempts("erro anterior 1");
        compensacaoEstoque.incrementarAttempts("erro anterior 2");

        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoEstoque));

        FeignException serverError = feignExceptionWithStatus(500);
        doThrow(serverError).when(estoqueClient).cancelarReserva("reserva-001");

        publisher.processarCompensacoesPendentes();

        assertThat(compensacaoEstoque.getStatus()).isEqualTo(StatusCompensacao.FALHA);
        assertThat(compensacaoEstoque.getAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("deve incrementar attempts quando erro 500 e tentar novamente")
    void deveIncrementarAttemptsQuandoErro500() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoEstoque));

        FeignException serverError = feignExceptionWithStatus(500);
        doThrow(serverError).when(estoqueClient).cancelarReserva("reserva-001");

        publisher.processarCompensacoesPendentes();

        assertThat(compensacaoEstoque.getStatus()).isEqualTo(StatusCompensacao.PENDENTE);
        assertThat(compensacaoEstoque.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("deve processar multiplos compensacoes em lote")
    void deveProcessarMultiplosCompensacoesEmLote() {
        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(compensacaoEstoque, compensacaoFrete));

        publisher.processarCompensacoesPendentes();

        verify(estoqueClient).cancelarReserva("reserva-001");
        verify(freteClient).cancelar("frete-001");
        assertThat(compensacaoEstoque.getStatus()).isEqualTo(StatusCompensacao.ENVIADO);
        assertThat(compensacaoFrete.getStatus()).isEqualTo(StatusCompensacao.ENVIADO);
    }

    @Test
    @DisplayName("processarCompensacaoManual deve retornar true quando sucesso")
    void processarCompensacaoManualDeveRetornarTrueQuandoSucesso() {
        when(jpaRepository.findById(1L)).thenReturn(Optional.of(compensacaoEstoque));

        boolean resultado = publisher.processarCompensacaoManual(1L);

        assertThat(resultado).isTrue();
        verify(estoqueClient).cancelarReserva("reserva-001");
    }

    @Test
    @DisplayName("processarCompensacaoManual deve retornar false quando ID nao existe")
    void processarCompensacaoManualDeveRetornarFalseQuandoIdNaoExiste() {
        when(jpaRepository.findById(99L)).thenReturn(Optional.empty());

        boolean resultado = publisher.processarCompensacaoManual(99L);

        assertThat(resultado).isFalse();
    }

    private FeignException feignExceptionWithStatus(int status) {
        return FeignException.errorStatus("test",
                Response.builder()
                        .request(Request.create(Request.HttpMethod.DELETE, "http://test",
                                Collections.emptyMap(), null, StandardCharsets.UTF_8,
                                new RequestTemplate()))
                        .status(status)
                        .reason("Error " + status)
                        .body("", StandardCharsets.UTF_8)
                        .build());
    }
}
