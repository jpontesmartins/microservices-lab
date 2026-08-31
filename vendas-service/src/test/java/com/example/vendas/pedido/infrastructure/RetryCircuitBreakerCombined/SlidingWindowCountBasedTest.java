package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de sliding window COUNT_BASED combinado com Retry.
 *
 * <p>Valida que cada tentativa de retry conta como uma chamada separada
 * no CircuitBreaker, preenchendo a janela deslizante mais rapidamente
 * do que chamadas sem retry.</p>
 */
class SlidingWindowCountBasedTest {

    @Nested
    @DisplayName("Sliding window COUNT_BASED com retry")
    class JanelaCountBasedTest {

        @Test
        @DisplayName("retry com 3 tentativas preenche a janela rapidamente (3 falhas por chamada)")
        void retryPreencheJanelaRapidamente() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .minimumNumberOfCalls(3)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("combined-janela-5");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-janela-5-r");

            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        ioFail("Falha");
                        return null;
                    }));

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("1a chamada retry = 3 falhas, nao abre se < minCalls=5")
        void janela10NaoAbreComMenosDeMinCalls() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("combined-janela-10");

            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-janela-10-r");

            AtomicInteger totalAttempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        int attempt = totalAttempts.incrementAndGet();
                        if (attempt <= 3) {
                            ioFail("Falha");
                        }
                        return "ok";
                    }));

            try { decorated.get(); } catch (RuntimeException ignored) { }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            try { decorated.get(); } catch (RuntimeException ignored) { }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
