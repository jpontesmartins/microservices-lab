package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * Teste do ciclo completo de recuperacao do CircuitBreaker com Retry.
 *
 * <p>Valida o fluxo OPEN → HALF_OPEN → CLOSED quando o servico se recupera
 * e o Retry funciona normalmente durante o estado HALF_OPEN.</p>
 */
class CicloCompletoTest {

    @Test
    @DisplayName("deve recuperar de OPEN para CLOSED usando retry no HALF_OPEN")
    void cicloCompletoComRetry() throws InterruptedException {
        CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfigWithShortWait())
                .circuitBreaker("combined-ciclo-completo");
        Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-ciclo-completo-r");

        Supplier<String> alwaysFail = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> {
                    ioFail("Falha");
                    return null;
                }));

        for (int i = 0; i < 2; i++) {
            try { alwaysFail.get(); } catch (RuntimeException ignored) {
            }
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        TimeUnit.MILLISECONDS.sleep(600);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        AtomicInteger halfOpenAttempts = new AtomicInteger(0);
        Supplier<String> halfOpenSuccess = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> {
                    halfOpenAttempts.incrementAndGet();
                    return "ok";
                }));

        halfOpenSuccess.get();
        halfOpenSuccess.get();
        halfOpenSuccess.get();

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED));

        assertThat(halfOpenAttempts.get()).isEqualTo(3);
    }
}
