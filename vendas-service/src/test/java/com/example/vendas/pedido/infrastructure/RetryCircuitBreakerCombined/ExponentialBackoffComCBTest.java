package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de exponential backoff combinado com CircuitBreaker.
 *
 * <p>Valida que o intervalo entre tentativas de retry aumenta
 * exponencialmente mesmo quando o CircuitBreaker esta envolvido,
 * respeitando o {@code exponentialBackoffMultiplier}.</p>
 */
class ExponentialBackoffComCBTest {

    @Test
    @DisplayName("retry com backoff deve aumentar tempo entre tentativas mesmo com CB")
    void backoffExponencialComCB() {
        CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-backoff-cb");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(4)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2))
                .retryExceptions(IOException.class, RuntimeException.class)
                .build();
        Retry retry = RetryRegistry.of(retryConfig).retry("combined-backoff-cb-r");

        AtomicInteger attempts = new AtomicInteger(0);
        long[] timestamps = new long[4];

        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> {
                    int attempt = attempts.getAndIncrement();
                    timestamps[attempt] = System.currentTimeMillis();
                    ioFail();
                    return null;
                }));

        try {
            decorated.get();
        } catch (RuntimeException ignored) {
        }

        assertThat(attempts.get()).isEqualTo(4);

        long wait1 = timestamps[1] - timestamps[0];
        long wait2 = timestamps[2] - timestamps[1];
        long wait3 = timestamps[3] - timestamps[2];

        assertThat(wait1).isGreaterThanOrEqualTo(80);
        assertThat(wait2).isGreaterThanOrEqualTo(160);
        assertThat(wait3).isGreaterThanOrEqualTo(300);
    }
}
