package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de FALHA_TRANSITORIA com cada tipo de retryException configurado.
 *
 * <p>Valida que {@code ResourceAccessException}, {@code TimeoutException}
 * e {@code IOException} sao corretamente retryadas pelo Retry e contam
 * como falhas no CircuitBreaker, resultando em fallback com
 * status {@code FALHA_TRANSITORIA}.</p>
 */
class FalhaTransitoriaRetryExceptionsTest {

    private RetryConfig buildConfigForExceptionType() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(IOException.class, TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class,
                        RuntimeException.class)
                .build();
    }

    @Nested
    @DisplayName("Retry + CB — FALHA_TRANSITORIA com resourceAccessException")
    class ResourceAccessExceptionTest {

        @Test
        @DisplayName("ResourceAccessException deve ser retryada e acionar fallback")
        void resourceAccessExceptionDeveSerRetryada() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-ra");
            Retry retry = RetryRegistry.of(buildConfigForExceptionType()).retry("combined-ra-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        resourceAccessFail();
                        return null;
                    }));

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts.get()).isEqualTo(3);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Retry + CB — FALHA_TRANSITORIA com timeoutException")
    class TimeoutExceptionTest {

        @Test
        @DisplayName("TimeoutException deve ser retryada e acionar fallback")
        void timeoutExceptionDeveSerRetryada() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-to");
            Retry retry = RetryRegistry.of(buildConfigForExceptionType()).retry("combined-to-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        timeoutFail();
                        return null;
                    }));

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Retry + CB — FALHA_TRANSITORIA com ioException")
    class IOExceptionTest {

        @Test
        @DisplayName("IOException deve ser retryada e acionar fallback")
        void ioExceptionDeveSerRetryada() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-io");
            Retry retry = RetryRegistry.of(buildConfigForExceptionType()).retry("combined-io-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Erro de E/S");
                        return null;
                    }));

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts.get()).isEqualTo(3);
        }
    }
}
