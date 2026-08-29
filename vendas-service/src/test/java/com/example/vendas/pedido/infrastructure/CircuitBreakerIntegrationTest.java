package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.domain.port.IntegracoesPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integracao completos: Resilience4j + Feign + LoadBalancer.
 * Valida o comportamento real do @CircuitBreaker com o contexto Spring.
 */
@SpringBootTest
class CircuitBreakerIntegrationTest {

    @Autowired
    private IntegracoesService integracoesService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("Estoque - Circuit Breaker com @CircuitBreaker annotation")
    class EstoqueCircuitBreakerTests {

        @Test
        @DisplayName("deve acionar fallback quando estoque-service nao esta disponivel")
        void deveAcionarFallbackQuandoEstoqueServiceNaoEstaDisponivel() {
            IntegracoesPort.ReservaEstoqueResult result = integracoesService.reservarEstoque(
                    "pedido-001", "SKU-ABC", 2);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.reservaId()).isNull();
        }

        @Test
        @DisplayName("deve abrir circuit breaker apos 5 falhas consecutivas")
        void deveAbrirCircuitBreakerApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                IntegracoesPort.ReservaEstoqueResult result = integracoesService.reservarEstoque(
                        "pedido-" + i, "SKU-ABC", 2);
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("estoque");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("deve retornar fallback imediato quando circuit breaker esta OPEN")
        void deveRetornarFallbackImediatoQuandoCircuitBreakerEstaOpen() {
            for (int i = 0; i < 5; i++) {
                integracoesService.reservarEstoque("pedido-" + i, "SKU-ABC", 2);
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("estoque");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            IntegracoesPort.ReservaEstoqueResult result = integracoesService.reservarEstoque(
                    "pedido-extra", "SKU-ABC", 2);

            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Frete - Circuit Breaker com @CircuitBreaker annotation")
    class FreteCircuitBreakerTests {

        @Test
        @DisplayName("deve acionar fallback quando frete-service nao esta disponivel")
        void deveAcionarFallbackQuandoFreteServiceNaoEstaDisponivel() {
            IntegracoesPort.FreteResult result = integracoesService.calcularFrete(
                    "pedido-001", "SKU-ABC", 2, "01310-100");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.freteId()).isNull();
            assertThat(result.valorFrete()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("deve abrir circuit breaker de frete apos 5 falhas consecutivas")
        void deveAbrirCircuitBreakerDeFreteApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                IntegracoesPort.FreteResult result = integracoesService.calcularFrete(
                        "pedido-" + i, "SKU-ABC", 2, "01310-100");
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("frete");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Pagamento - Circuit Breaker com @CircuitBreaker annotation")
    class PagamentoCircuitBreakerTests {

        @Test
        @DisplayName("deve acionar fallback quando pagamento-service nao esta disponivel")
        void deveAcionarFallbackQuandoPagamentoServiceNaoEstaDisponivel() {
            IntegracoesPort.PagamentoResult result = integracoesService.processarPagamento(
                    "pedido-001", 140.50);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.transacaoId()).isNull();
        }

        @Test
        @DisplayName("deve abrir circuit breaker de pagamento apos 5 falhas consecutivas")
        void deveAbrirCircuitBreakerDePagamentoApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                IntegracoesPort.PagamentoResult result = integracoesService.processarPagamento(
                        "pedido-" + i, 100.0);
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pagamento");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Fallback via @CircuitBreaker annotation - Chamada direta")
    class FallbackViaAnnotationTests {

        @Test
        @DisplayName("fallback de estoque deve retornar status FALHA_TRANSITORIA")
        void fallbackDeEstoqueDeveRetornarStatusFalhaTransitoria() {
            IntegracoesPort.ReservaEstoqueResult fallback = integracoesService.reservaFallback(
                    "pedido-001", "SKU-ABC", 2,
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.reservaId()).isNull();
        }

        @Test
        @DisplayName("fallback de frete deve retornar status FALHA_TRANSITORIA")
        void fallbackDeFreteDeveRetornarStatusFalhaTransitoria() {
            IntegracoesPort.FreteResult fallback = integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100",
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.freteId()).isNull();
            assertThat(fallback.valorFrete()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("fallback de pagamento deve retornar status FALHA_TRANSITORIA")
        void fallbackDePagamentoDeveRetornarStatusFalhaTransitoria() {
            IntegracoesPort.PagamentoResult fallback = integracoesService.pagamentoFallback(
                    "pedido-001", 140.50,
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.transacaoId()).isNull();
        }
    }
}
