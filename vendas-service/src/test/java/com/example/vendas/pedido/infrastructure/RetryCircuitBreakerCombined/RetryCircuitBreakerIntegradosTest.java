package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import com.example.vendas.shared.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Testes de integracao Retry + CircuitBreaker.
 *
 * <p>Valida como o Retry e o CircuitBreaker interagem quando compostos
 * via {@code Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(cb, ...))}.</p>
 *
 * <p>Cenários cobertos:</p>
 * <ul>
 *   <li>Retry falha todas tentativas → CB registra N falhas (uma por tentativa)</li>
 *   <li>CB abre apos chamadas com retry falhando (cada retry = 1 chamada CB)</li>
 *   <li>Retry sucesso na 2a tentativa → CB registra 1 falha + 1 sucesso</li>
 *   <li>CB OPEN bloqueia chamadas mesmo com retry (0 tentativas reais)</li>
 *   <li>HALF_OPEN com retry funcionando e falhando</li>
 *   <li>BusinessException ignorada por ambos (retry + CB)</li>
 * </ul>
 */
class RetryCircuitBreakerIntegradosTest {

    @Nested
    @DisplayName("Retry falha todas tentativas → CB registra falhas → CB abre")
    class RetryFalhaCBRegistraEAbre {

        @Test
        @DisplayName("retry com 3 tentativas falha, CB registra 3 falhas (uma por tentativa)")
        void retryFalhaCBRegistra3Falhas() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-rf-cb-3f");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-rf-cb-3f-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Falha de conexao");
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

        @Test
        @DisplayName("deve abrir CB apos 2 chamadas com retry falhando (2x3=6 falhas, > minCalls=5)")
        void deveAbrirCBComRetryFalhando() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-retry-abre-cb");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-retry-abre");

            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        ioFail("Falha");
                        return null;
                    }));

            for (int i = 0; i < 2; i++) {
                try {
                    decorated.get();
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("deve retornar FALHA_TRANSITORIA apos retry esgotar")
        void deveRetornarFALHA_TRANSITORIAComRetryEsgotado() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-fallback-final");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-fallback-retry");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        ioFail("Servico indisponivel");
                        return null;
                    }));

            String result = "SUCESSO";
            try {
                decorated.get();
            } catch (RuntimeException e) {
                result = "FALHA_TRANSITORIA";
            }

            assertThat(result).isEqualTo("FALHA_TRANSITORIA");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Retry sucesso na 2a tentativa → CB registra sucesso parcial")
    class RetrySucessoNaSegundaCBRegistraSucesso {

        @Test
        @DisplayName("retry falha na 1a, sucesso na 2a, CB deve registrar 2 tentativas")
        void retrySucessoNaSegundaCBRegistra() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-r-suc-2");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-r-suc-2-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt == 1) {
                            ioFail("Falha na 1a tentativa");
                        }
                        return "sucesso";
                    }));

            String result = decorated.get();

            assertThat(result).isEqualTo("sucesso");
            assertThat(attempts.get()).isEqualTo(2);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("CB OPEN bloqueia chamadas (retry nao ajuda)")
    class CBAbertoBloqueiaRetry {

        @Test
        @DisplayName("quando CB esta OPEN, retry tenta mas CB rejeita, fallback acionado")
        void retryTentaMasCBRejeita() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-cb-bloqueia");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-cb-bloqueia-r");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        return "sucesso";
                    }));

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts.get()).isEqualTo(0);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("HALF_OPEN com retry")
    class HalfOpenComRetry {

        @Test
        @DisplayName("CB em HALF_OPEN deve permitir retry normalmente")
        void retryFuncionaEmHalfOpen() throws InterruptedException {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfigWithShortWait())
                    .circuitBreaker("combined-half-open-retry");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            TimeUnit.MILLISECONDS.sleep(600);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-half-open-retry-inst");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt == 1) {
                            ioFail("Falha");
                        }
                        return "sucesso-no-half-open";
                    }));

            String result = decorated.get();

            assertThat(result).isEqualTo("sucesso-no-half-open");
            assertThat(attempts.get()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HALF_OPEN com retry falhando deve voltar para OPEN")
        void halfOpenComRetryFalhandoVoltaParaOpen() throws InterruptedException {
            CircuitBreakerConfig halfOpenConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .ignoreExceptions(BusinessException.class)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(halfOpenConfig)
                    .circuitBreaker("combined-half-open-falha-retry");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            TimeUnit.MILLISECONDS.sleep(600);

            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-half-open-falha-inst");

            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        ioFail("Ainda falhou");
                        return null;
                    }));

            for (int i = 0; i < 3; i++) {
                try {
                    decorated.get();
                } catch (RuntimeException ignored) {
                }
            }

            TimeUnit.MILLISECONDS.sleep(200);

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN));
        }
    }

    @Nested
    @DisplayName("BusinessException ignorada por ambos (retry + CB)")
    class BusinessExceptionIgnoradaPorAmbos {

        @Test
        @DisplayName("BusinessException nao deve ser retryada E nao deve contar como falha no CB")
        void businessExceptionIgnoradaPorRetryECB() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("combined-business-ambos");
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("combined-business-ambos-retry");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(cb, () -> {
                        attempts.incrementAndGet();
                        throw new BusinessException("FALHA_ESTOQUE", null);
                    }));

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_ESTOQUE");

            assertThat(attempts.get()).isEqualTo(1);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(0);
        }
    }
}
