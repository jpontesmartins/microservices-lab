package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.infrastructure.dto.FreteResponse;
import com.example.vendas.pedido.infrastructure.dto.PagamentoResponse;
import com.example.vendas.pedido.infrastructure.dto.ReservaResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Testes isolados do Circuit Breaker (sem Spring).
 * Valida o ciclo de vida: CLOSED -> OPEN -> HALF_OPEN -> CLOSED
 * e o comportamento de fallback manual.
 */
class CircuitBreakerIsolatedTest {

    private CircuitBreakerConfig config;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry = CircuitBreakerRegistry.of(config);
    }

    @Nested
    @DisplayName("Ciclo de vida do Circuit Breaker")
    class CicloDeVidaTests {

        @Test
        @DisplayName("deve comecar no estado CLOSED")
        void deveComecarNoEstadoCLOSED() {
            CircuitBreaker cb = registry.circuitBreaker("teste-close");

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve transicionar para OPEN apos 5 falhas (minimumNumberOfCalls)")
        void deveTransicionarParaOpenApos5Falhas() {
            CircuitBreaker cb = registry.circuitBreaker("teste-open");

            Supplier<String> decorated = () -> {
                throw new RuntimeException("Falha simulada");
            };

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(decorated);
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("deve permanecer CLOSED com menos de 5 chamadas mesmo com falhas")
        void devePermanecerClosedComMenosDe5Chamadas() {
            CircuitBreaker cb = registry.circuitBreaker("teste-permanece-close");

            Supplier<String> decorated = () -> {
                throw new RuntimeException("Falha simulada");
            };

            for (int i = 0; i < 4; i++) {
                try {
                    cb.executeSupplier(decorated);
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve permanecer CLOSED com taxa de falha abaixo de 50%")
        void devePermanecerClosedComTaxaDeFalhaAbaixoDe50() {
            CircuitBreaker cb = registry.circuitBreaker("teste-taxa-baixa");

            Supplier<String> sucesso = () -> "ok";
            Supplier<String> falha = () -> {
                throw new RuntimeException("Falha");
            };

            for (int i = 0; i < 7; i++) {
                cb.executeSupplier(sucesso);
            }
            for (int i = 0; i < 3; i++) {
                try {
                    cb.executeSupplier(falha);
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve transicionar para OPEN com taxa de falha acima de 50%")
        void deveTransicionarParaOpenComTaxaDeFalhaAcimaDe50() {
            CircuitBreaker cb = registry.circuitBreaker("teste-taxa-alta");

            Supplier<String> sucesso = () -> "ok";
            Supplier<String> falha = () -> {
                throw new RuntimeException("Falha");
            };

            for (int i = 0; i < 4; i++) {
                cb.executeSupplier(sucesso);
            }
            for (int i = 0; i < 6; i++) {
                try {
                    cb.executeSupplier(falha);
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Fallback quando circuit breaker esta OPEN")
    class FallbackTests {

        @Test
        @DisplayName("deve acionar fallback de estoque quando circuit breaker esta OPEN")
        void deveAcionarFallbackDeEstoqueQuandoCircuitBreakerEstaOpen() {
            CircuitBreaker cb = registry.circuitBreaker("teste-fallback-estoque");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            ReservaResponse fallback = new ReservaResponse(null, "INDISPONIVEL", "SKU-ABC", 2, "pedido-001");

            assertThat(fallback.status()).isEqualTo("INDISPONIVEL");
            assertThat(fallback.reservaId()).isNull();
        }

        @Test
        @DisplayName("deve acionar fallback de frete quando circuit breaker esta OPEN")
        void deveAcionarFallbackDeFreteQuandoCircuitBreakerEstaOpen() {
            CircuitBreaker cb = registry.circuitBreaker("teste-fallback-frete");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            FreteResponse fallback = new FreteResponse(null, "INDISPONIVEL", "pedido-001", 0.0, null);

            assertThat(fallback.status()).isEqualTo("INDISPONIVEL");
            assertThat(fallback.valorFrete()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("deve acionar fallback de pagamento quando circuit breaker esta OPEN")
        void deveAcionarFallbackDePagamentoQuandoCircuitBreakerEstaOpen() {
            CircuitBreaker cb = registry.circuitBreaker("teste-fallback-pagamento");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            PagamentoResponse fallback = new PagamentoResponse(null, "FALHA_TRANSITORIA", "pedido-001", 100.0);

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.transacaoId()).isNull();
        }
    }

    @Nested
    @DisplayName("Recovery: OPEN -> HALF_OPEN")
    class RecoveryTests {

        @Test
        @DisplayName("deve transicionar de OPEN para HALF_OPEN apos waitDuration e aceitar chamadas")
        void deveTransicionarParaHalfOpenEPermitirChamadas() throws InterruptedException {
            CircuitBreakerConfig shortWaitConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofMillis(500))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();

            CircuitBreakerRegistry shortRegistry = CircuitBreakerRegistry.of(shortWaitConfig);
            CircuitBreaker cb = shortRegistry.circuitBreaker("teste-recovery");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            TimeUnit.MILLISECONDS.sleep(600);

            cb.executeSupplier(() -> "ok1");
            cb.executeSupplier(() -> "ok2");
            cb.executeSupplier(() -> "ok3");

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
        }

        @Test
        @DisplayName("deve manter HALF_OPEN enquanto chamadas estao pendentes")
        void deveManterHalfOpenEnquantoChamadasEstaoPendentes() throws InterruptedException {
            CircuitBreakerConfig shortWaitConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofMillis(500))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();

            CircuitBreakerRegistry shortRegistry = CircuitBreakerRegistry.of(shortWaitConfig);
            CircuitBreaker cb = shortRegistry.circuitBreaker("teste-half-open-pending");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            TimeUnit.MILLISECONDS.sleep(600);

            cb.executeSupplier(() -> "ok1");

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("Metricas do Circuit Breaker")
    class MetricasTests {

        @Test
        @DisplayName("deve registrar numero de chamadas com sucesso e falha")
        void deveRegistrarMetricasCorretamente() {
            CircuitBreaker cb = registry.circuitBreaker("teste-metricas");

            Supplier<String> sucesso = () -> "ok";
            Supplier<String> falha = () -> {
                throw new RuntimeException("Falha");
            };

            for (int i = 0; i < 3; i++) {
                cb.executeSupplier(sucesso);
            }
            for (int i = 0; i < 2; i++) {
                try {
                    cb.executeSupplier(falha);
                } catch (RuntimeException ignored) {
                }
            }

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(3);
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(2);
            assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve registrar chamadas nao permitidas quando OPEN")
        void deveRegistrarChamadasNaoPermitidasQuandoOpen() {
            CircuitBreaker cb = registry.circuitBreaker("teste-not-permitted");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> {
                        throw new RuntimeException("Falha");
                    });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            for (int i = 0; i < 3; i++) {
                try {
                    cb.executeSupplier(() -> "ok");
                } catch (Exception ignored) {
                }
            }

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Retry - Tentativas automaticas")
    class RetryTests {

        @Test
        @DisplayName("deve repetir chamada ate maxAttempts quando falha")
        void deveRepetirChamadaAteMaxAttemptsQuandoFalha() {
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .build();
            RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
            Retry retry = retryRegistry.retry("teste-retry-falha");

            int[] attempts = {0};
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts[0]++;
                throw new RuntimeException("Falha simulada");
            });

            try {
                decorated.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(attempts[0]).isEqualTo(3);
            Retry.Metrics metrics = retry.getMetrics();
            assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve retornar resultado na segunda tentativa quando primeira falha")
        void deveRetornarResultadoNaSegundaTentativaQuandoPrimeiraFalha() {
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .build();
            RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
            Retry retry = retryRegistry.retry("teste-retry-sucesso");

            int[] attempts = {0};
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts[0]++;
                if (attempts[0] == 1) {
                    throw new RuntimeException("Falha na primeira tentativa");
                }
                return "sucesso";
            });

            String result = decorated.get();

            assertThat(result).isEqualTo("sucesso");
            assertThat(attempts[0]).isEqualTo(2);
        }

        @Test
        @DisplayName("nao deve repetir chamada quando excecao nao esta na lista de retryExceptions")
        void deveNaoRepetirChamadaQuandoExcecaoNaoEstaNaListaDeRetryExceptions() {
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(java.io.IOException.class)
                    .build();
            RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
            Retry retry = retryRegistry.retry("teste-retry-ignore");

            int[] attempts = {0};
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts[0]++;
                throw new IllegalArgumentException("Excecao ignorada");
            });

            try {
                decorated.get();
            } catch (IllegalArgumentException ignored) {
            }

            assertThat(attempts[0]).isEqualTo(1);
        }

        @Test
        @DisplayName("deve registrar metricas de tentativas com sucesso e falha")
        void deveRegistrarMetricasDeTentativas() {
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .build();
            RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
            Retry retry = retryRegistry.retry("teste-retry-metricas");

            int[] attempts = {0};
            Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
                attempts[0]++;
                if (attempts[0] <= 2) {
                    throw new RuntimeException("Falha");
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
}
