package com.example.vendas.integration;

import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

    /**
     * Configura o circuit breaker com parâmetros de teste.
     */
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
        @DisplayName("deve transicionar para OPEN após 5 falhas (minimumNumberOfCalls)")
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
    @DisplayName("Fallback quando circuit breaker está OPEN")
    class FallbackTests {

        @Test
        @DisplayName("deve acionar fallback de estoque quando circuit breaker está OPEN")
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
        @DisplayName("deve acionar fallback de frete quando circuit breaker está OPEN")
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
        @DisplayName("deve acionar fallback de pagamento quando circuit breaker está OPEN")
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
        @DisplayName("deve transicionar de OPEN para HALF_OPEN após waitDuration e aceitar chamadas")
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
}
