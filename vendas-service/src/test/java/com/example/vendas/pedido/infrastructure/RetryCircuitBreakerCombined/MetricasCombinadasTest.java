package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import com.example.vendas.shared.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de metricas combinadas de Retry + CircuitBreaker.
 *
 * <p>Valida que as metricas de ambos os padroes sao registradas corretamente
 * quando compostos, incluindo contagem de falhas, sucessos e chamadas
 * nao permitidas (not-permitted).</p>
 */
class MetricasCombinadasTest {

    @Test
    @DisplayName("deve registrar metricas corretas: 6 falhas CB + 1 sucesso CB, 2 com retry + 1 sem retry")
    void metricasApos2Falhas1Sucesso() {
        CircuitBreakerConfig customCBConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(15)
                .minimumNumberOfCalls(15)
                .failureRateThreshold(50)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(BusinessException.class)
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(customCBConfig).circuitBreaker("combined-m-2f1s");
        Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-m-2f1s-r");

        AtomicInteger totalAttempts = new AtomicInteger(0);
        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> {
                    int attempt = totalAttempts.incrementAndGet();
                    if (attempt <= 6) {
                        ioFail("Falha");
                    }
                    return "ok";
                }));

        String r1 = "FALHA_TRANSITORIA";
        try { r1 = decorated.get(); } catch (RuntimeException e) { r1 = "FALHA_TRANSITORIA"; }
        String r2 = "FALHA_TRANSITORIA";
        try { r2 = decorated.get(); } catch (RuntimeException e) { r2 = "FALHA_TRANSITORIA"; }
        String r3 = "FALHA_TRANSITORIA";
        try { r3 = decorated.get(); } catch (RuntimeException e) { r3 = "FALHA_TRANSITORIA"; }

        assertThat(r1).isEqualTo("FALHA_TRANSITORIA");
        assertThat(r2).isEqualTo("FALHA_TRANSITORIA");
        assertThat(r3).isEqualTo("ok");

        CircuitBreaker.Metrics cbMetrics = cb.getMetrics();
        assertThat(cbMetrics.getNumberOfFailedCalls()).isEqualTo(6);
        assertThat(cbMetrics.getNumberOfSuccessfulCalls()).isEqualTo(1);

        Retry.Metrics retryMetrics = retry.getMetrics();
        assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(2);
        assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("deve registrar not-permitted quando CB esta OPEN apos retry esgotar")
    void metricasNotPermittedAposRetryEsgotar() {
        CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-m-not-perm");
        Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-m-not-perm-r");

        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> {
                    ioFail("Falha");
                    return null;
                }));

        for (int i = 0; i < 3; i++) {
            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        for (int i = 0; i < 2; i++) {
            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }
        }

        CircuitBreaker.Metrics metrics = cb.getMetrics();
        assertThat(metrics.getNumberOfNotPermittedCalls()).isGreaterThan(0);
    }
}
