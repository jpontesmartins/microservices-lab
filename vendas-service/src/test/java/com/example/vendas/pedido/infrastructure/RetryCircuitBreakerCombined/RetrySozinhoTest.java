package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import com.example.vendas.shared.exception.BusinessException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitarios do Retry isoladamente (sem CircuitBreaker).
 *
 * <p>Valida o comportamento base do Retry conforme a configuracao do
 * {@code application.yml} do vendas-service: maxAttempts=3,
 * exponentialBackoff, retryExceptions e ignoreExceptions.</p>
 *
 * <p>Cenários cobertos:</p>
 * <ul>
 *   <li>Todas tentativas falham → excecao propagada apos maxAttempts</li>
 *   <li>Sucesso na 2a/3a tentativa → retry funciona corretamente</li>
 *   <li>BusinessException ignorada pelo retry (ignoreExceptions)</li>
 *   <li>Excecao nao-listada nao e retryada</li>
 *   <li>Exponential backoff entre tentativas</li>
 * </ul>
 */
class RetrySozinhoTest {

    @Nested
    @DisplayName("Todas tentativas falham → FALHA_TRANSITORIA")
    class TodasTentativasFalham {

        @Test
        @DisplayName("deve executar exatamente maxAttempts=3 vezes quando todas falham")
        void deveExecutar3VezesQuandoTodasFalham() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-todas-falham");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts.incrementAndGet();
                ioFail();
                return null;
            });

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("deve propagar RuntimeException quando todas tentativas falham")
        void devePropagarExcecaoQuandoTodasFalham() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-propagar-excecao");

            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                ioFail("Timeout de rede");
                return null;
            });

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("deve registrar metricas de falha com retry")
        void deveRegistrarMetricasDeFalhaComRetry() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-metricas-falha");

            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                ioFail();
                return null;
            });

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            Retry.Metrics metrics = retry.getMetrics();
            assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);
            assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Sucesso na segunda tentativa")
    class SucessoNaSegundaTentativa {

        @Test
        @DisplayName("deve retornar resultado quando segunda tentativa tem sucesso")
        void deveRetornarResultadoNaSegundaTentativa() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-sucesso-segunda");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    ioFail("Falha na 1a tentativa");
                }
                return "sucesso";
            });

            String result = decorated.get();

            assertThat(result).isEqualTo("sucesso");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("deve registrar metricas de sucesso com retry")
        void deveRegistrarMetricasDeSucessoComRetry() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-metricas-sucesso");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt <= 2) {
                    ioFail("Falha");
                }
                return "ok";
            });

            String result = decorated.get();

            assertThat(result).isEqualTo("ok");
            Retry.Metrics metrics = retry.getMetrics();
            assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
            assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Sucesso na terceira tentativa")
    class SucessoNaTerceiraTentativa {

        @Test
        @DisplayName("deve retornar resultado quando terceira tentativa tem sucesso")
        void deveRetornarResultadoNaTerceiraTentativa() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-sucesso-terceira");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt <= 2) {
                    ioFail("Falha nas primeiras 2 tentativas");
                }
                return "sucesso-na-terceira";
            });

            String result = decorated.get();

            assertThat(result).isEqualTo("sucesso-na-terceira");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("BusinessException NAO e retryada (ignoreExceptions)")
    class BusinessExceptionNaoRetryada {

        @Test
        @DisplayName("deve executar apenas 1 vez quando BusinessException e lancada")
        void deveExecutarApenas1VezQuandoBusinessException() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-business-ignorada");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts.incrementAndGet();
                throw new BusinessException("FALHA_ESTOQUE", null);
            });

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FALHA_ESTOQUE");

            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("BusinessException nao deve incrementar metricas de retry")
        void businessExceptionNaoDeveIncrementarMetricasDeRetry() {
            Retry retry = RetryRegistry.of(buildRetryConfig()).retry("retry-business-metricas");

            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                throw new BusinessException("FALHA_ESTOQUE", null);
            });

            try {
                decorated.get();
            } catch (BusinessException ignored) {
            }

            Retry.Metrics metrics = retry.getMetrics();
            assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
            assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Excecao nao-listada nao e retryada")
    class ExcecaoNaoListada {

        @Test
        @DisplayName("nao deve retryar IllegalArgumentException")
        void naoDeveRetryarExcecaoNaoListada() {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(IOException.class)
                    .build();
            Retry retry = RetryRegistry.of(config).retry("retry-excecao-nao-listada");

            AtomicInteger attempts = new AtomicInteger(0);
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("Arg invalido");
            });

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(attempts.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Exponential Backoff")
    class ExponentialBackoff {

        @Test
        @DisplayName("deve aplicar backoff exponencial entre tentativas")
        void deveAplicarBackoffExponencial() {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(4)
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2))
                    .retryExceptions(IOException.class, RuntimeException.class)
                    .build();
            Retry retry = RetryRegistry.of(config).retry("retry-backoff");

            AtomicInteger attempts = new AtomicInteger(0);
            long[] timestamps = new long[4];

            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                int attempt = attempts.getAndIncrement();
                timestamps[attempt] = System.currentTimeMillis();
                ioFail();
                return null;
            });

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
}
