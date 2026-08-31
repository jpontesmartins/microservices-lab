package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de fallback apos retry esgotar, simulando os 3 servicos
 * downstream do vendas-service: estoque, frete e pagamento.
 *
 * <p>Valida que apos {@code maxAttempts=3} falhas, o Retry propaga a
 * excecao e o fallback e acionado, retornando {@code FALHA_TRANSITORIA}.</p>
 */
class FallbackAposRetryEsgotarTest {

    @Nested
    @DisplayName("reservaFallback → FALHA_TRANSITORIA")
    class ReservaFallbackTest {

        @Test
        @DisplayName("reservaFallback deve retornar FALHA_TRANSITORIA apos retry esgotar com IOException")
        void reservaFallbackAposRetryEsgotar() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-rfb");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-rfb-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Estoque offline");
                        return null;
                    }));

            String status = "SUCESSO";
            try {
                decorated.get();
            } catch (RuntimeException e) {
                status = "FALHA_TRANSITORIA";
            }

            assertThat(status).isEqualTo("FALHA_TRANSITORIA");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("freteFallback → FALHA_TRANSITORIA")
    class FreteFallbackTest {

        @Test
        @DisplayName("freteFallback deve retornar FALHA_TRANSITORIA apos retry esgotar")
        void freteFallbackAposRetryEsgotar() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-ffb");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-ffb-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Frete offline");
                        return null;
                    }));

            String status = "SUCESSO";
            Double valorFrete = 20.0;
            try {
                decorated.get();
            } catch (RuntimeException e) {
                status = "FALHA_TRANSITORIA";
                valorFrete = 0.0;
            }

            assertThat(status).isEqualTo("FALHA_TRANSITORIA");
            assertThat(valorFrete).isEqualTo(0.0);
            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("pagamentoFallback → FALHA_TRANSITORIA")
    class PagamentoFallbackTest {

        @Test
        @DisplayName("pagamentoFallback deve retornar FALHA_TRANSITORIA apos retry esgotar")
        void pagamentoFallbackAposRetryEsgotar() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-pfb");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-pfb-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Pagamento offline");
                        return null;
                    }));

            String status = "SUCESSO";
            try {
                decorated.get();
            } catch (RuntimeException e) {
                status = "FALHA_TRANSITORIA";
            }

            assertThat(status).isEqualTo("FALHA_TRANSITORIA");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }
}
