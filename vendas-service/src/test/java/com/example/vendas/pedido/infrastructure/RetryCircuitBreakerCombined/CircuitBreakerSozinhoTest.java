package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import com.example.vendas.shared.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined.Resilience4jTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Testes unitarios do CircuitBreaker isoladamente (sem Retry).
 *
 * <p>Valida o ciclo de vida do CircuitBreaker conforme a configuracao do
 * {@code application.yml} do vendas-service: COUNT_BASED(10),
 * minimumNumberOfCalls=5, failureRateThreshold=50%, slowCallRateThreshold=60%,
 * waitDurationInOpenState=10s, permittedNumberOfCallsInHalfOpenState=3.</p>
 *
 * <p>Cenários cobertos:</p>
 * <ul>
 *   <li>CLOSED → OPEN apos minimumNumberOfCalls com taxa >= 50%</li>
 *   <li>Permanece CLOSED com menos de 5 chamadas ou taxa abaixo de 50%</li>
 *   <li>OPEN rejeita chamadas imediatamente (not-permitted)</li>
 *   <li>Recovery: OPEN → HALF_OPEN → CLOSED</li>
 *   <li>Recovery: HALF_OPEN → falha → OPEN novamente</li>
 *   <li>BusinessException ignorada (nao conta como falha)</li>
 *   <li>Slow calls contam como insatisfatorias</li>
 * </ul>
 */
class CircuitBreakerSozinhoTest {

    @Nested
    @DisplayName("Abre apos minimumNumberOfCalls com taxa >= 50%")
    class AbreAposMinimumCalls {

        @Test
        @DisplayName("deve transicionar para OPEN apos 5 falhas consecutivas")
        void deveAbrirApos5Falhas() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-abre-5-falhas");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("deve permanecer CLOSED com menos de 5 chamadas")
        void devePermanecerClosedComMenosDe5Chamadas() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-permanece-closed-4");

            for (int i = 0; i < 4; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve permanecer CLOSED com taxa de falha abaixo de 50%")
        void devePermanecerClosedComTaxaAbaixo50() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-taxa-baixa");

            for (int i = 0; i < 7; i++) {
                cb.executeSupplier(() -> "ok");
            }
            for (int i = 0; i < 3; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve transicionar para OPEN com taxa exata de 50%")
        void deveAbrirComTaxaExataDe50() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-taxa-exata-50");

            for (int i = 0; i < 5; i++) {
                cb.executeSupplier(() -> "ok");
            }
            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN rejeita chamadas imediatamente")
    class OpenRejeitaChamadas {

        @Test
        @DisplayName("deve registrar chamadas nao permitidas quando OPEN")
        void deveRegistrarChamadasNaoPermitidas() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-not-permitted");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            for (int i = 0; i < 3; i++) {
                try {
                    cb.executeSupplier(() -> "ok");
                } catch (RuntimeException ignored) {
                }
            }

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Recovery: OPEN → HALF_OPEN → CLOSED")
    class Recovery {

        @Test
        @DisplayName("deve recuperar de OPEN para CLOSED apos waitDuration")
        void deveRecuperarDeOpenParaClosed() throws InterruptedException {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfigWithShortWait()).circuitBreaker("cb-recovery");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
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
        @DisplayName("deve voltar para OPEN quando HALF_OPEN recebe falhas")
        void deveVoltarParaOpenQuandoHalfOpenFalha() throws InterruptedException {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfigWithShortWait()).circuitBreaker("cb-half-open-falha");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            TimeUnit.MILLISECONDS.sleep(600);

            for (int i = 0; i < 3; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail("Falha no half-open"); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN));
        }

        @Test
        @DisplayName("deve manter HALF_OPEN enquanto chamadas estao pendentes")
        void deveManterHalfOpenEnquantoChamadasPendentes() throws InterruptedException {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfigWithShortWait()).circuitBreaker("cb-half-open-pendente");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail(); return null; });
                } catch (RuntimeException ignored) {
                }
            }

            TimeUnit.MILLISECONDS.sleep(600);

            cb.executeSupplier(() -> "ok1");

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("BusinessException ignorada (nao conta como falha)")
    class BusinessExceptionIgnorada {

        @Test
        @DisplayName("BusinessException nao deve contar para taxa de falha do CB")
        void businessExceptionNaoDeveContarParaTaxaDeFalha() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-business-ignorada");

            for (int i = 0; i < 8; i++) {
                try {
                    cb.executeSupplier(() -> { throw new BusinessException("FALHA_ESTOQUE", null); });
                } catch (BusinessException ignored) {
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
        }

        @Test
        @DisplayName("BusinessException nao deve contar como falha mas chamada e bloqueada quando OPEN")
        void businessExceptionNaoDeveGerarNotPermittedCalls() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(buildCBConfig()).circuitBreaker("cb-business-not-permitted");

            for (int i = 0; i < 5; i++) {
                try {
                    cb.executeSupplier(() -> { ioFail("Falha real"); return null; });
                } catch (RuntimeException ignored) {
                }
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            try {
                cb.executeSupplier(() -> { throw new BusinessException("FALHA_ESTOQUE", null); });
            } catch (BusinessException | io.github.resilience4j.circuitbreaker.CallNotPermittedException ignored) {
            }

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Slow calls (chamadas lentas)")
    class SlowCalls {

        @Test
        @DisplayName("chamadas lentas devem contar como insatisfatorias para slowCallRateThreshold")
        void chamadasLentasDevemContarComoInsatisfatorias() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(100)
                    .slowCallRateThreshold(60)
                    .slowCallDurationThreshold(Duration.ofMillis(200))
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("cb-slow-calls");

            for (int i = 0; i < 5; i++) {
                cb.executeSupplier(() -> {
                    try { TimeUnit.MILLISECONDS.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    return "lento";
                });
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("chamadas rapidas nao devem ser classificadas como lentas")
        void chamadasRapidasNaoDevemSerClassificadasComoLentas() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(100)
                    .slowCallRateThreshold(60)
                    .slowCallDurationThreshold(Duration.ofSeconds(2))
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("cb-fast-calls");

            for (int i = 0; i < 5; i++) {
                cb.executeSupplier(() -> "rapido");
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
