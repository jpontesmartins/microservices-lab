package com.example.vendas.integration;

import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração completos: Resilience4j + Feign + LoadBalancer.
 * Valida o comportamento real do @CircuitBreaker com o contexto Spring.
 *
 * <p>Os Feign clients reais são usados, mas como os serviços não estão rodando,
 * as chamadas falham com erros de conexão. O circuit breaker detecta essas
 * falhas e aciona os fallbacks automaticamente.</p>
 */
@SpringBootTest
class CircuitBreakerIntegrationTest {

    @Autowired
    private IntegracoesService integracoesService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Reseta todos os circuit breakers antes de cada teste.
     */
    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("Estoque - Circuit Breaker com @CircuitBreaker annotation")
    class EstoqueCircuitBreakerTests {

        /**
         * A falha e implicita: nenhum mock e configurado.
         * O Feign client tenta conectar via LoadBalancer, mas como Eureka esta
         * desabilitado no application.yml de teste (eureka.client.enabled=false)
         * e nenhum serviço está registrado, o RoundRobinLoadBalancer lança
         * IllegalStateException -> ServiceUnavailable. O @CircuitBreaker pega
         * essa excecao e invoca o fallback automaticamente.
         */
        @Test
        @DisplayName("deve acionar fallback quando estoque-service não está disponível")
        void deveAcionarFallbackQuandoEstoqueServiceNaoEstaDisponivel() {
            ReservaResponse result = integracoesService.reservarEstoque(
                    "pedido-001", "SKU-ABC", 2);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.reservaId()).isNull();
        }

        /**
         * A falha e implicita: a cadeia Feign -> LoadBalancer -> ServiceUnavailable
         * falha a cada chamada porque os serviços não estão registrados no Eureka.
         * Apos 5 chamadas (minimumNumberOfCalls) com 100% de falha (acima de 50%),
         * o circuit breaker transiciona para OPEN.
         */
        @Test
        @DisplayName("deve abrir circuit breaker após 5 falhas consecutivas")
        void deveAbrirCircuitBreakerApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                ReservaResponse result = integracoesService.reservarEstoque(
                        "pedido-" + i, "SKU-ABC", 2);
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("estoque");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        /**
         * Com o circuit breaker em estado OPEN, qualquer chamada e bloqueada
         * imediatamente (CallNotPermittedException) sem tentar conectar ao serviço.
         * O fallback e acionado instantaneamente.
         */
        @Test
        @DisplayName("deve retornar fallback imediato quando circuit breaker está OPEN")
        void deveRetornarFallbackImediatoQuandoCircuitBreakerEstaOpen() {
            for (int i = 0; i < 5; i++) {
                integracoesService.reservarEstoque("pedido-" + i, "SKU-ABC", 2);
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("estoque");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Esta chamada vai direto para o fallback (sem tentar conectar)
            ReservaResponse result = integracoesService.reservarEstoque(
                    "pedido-extra", "SKU-ABC", 2);

            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");

            CircuitBreaker.Metrics metrics = cb.getMetrics();
            assertThat(metrics.getNumberOfNotPermittedCalls()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Frete - Circuit Breaker com @CircuitBreaker annotation")
    class FreteCircuitBreakerTests {

        /**
         * Mesmo mecanismo do estoque: LoadBalancer não encontra serviços registrados,
         * lança ServiceUnavailable, e o @CircuitBreaker invoca o fallback.
         */
        @Test
        @DisplayName("deve acionar fallback quando frete-service não está disponível")
        void deveAcionarFallbackQuandoFreteServiceNaoEstaDisponivel() {
            FreteResponse result = integracoesService.calcularFrete(
                    "pedido-001", "SKU-ABC", 2, "01310-100");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.freteId()).isNull();
            assertThat(result.valorFrete()).isEqualTo(0.0);
        }

        /**
         * Mesma lógica do estoque: 5 falhas implícitas (serviços offline) abrem o circuit breaker.
         */
        @Test
        @DisplayName("deve abrir circuit breaker de frete após 5 falhas consecutivas")
        void deveAbrirCircuitBreakerDeFreteApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                FreteResponse result = integracoesService.calcularFrete(
                        "pedido-" + i, "SKU-ABC", 2, "01310-100");
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("frete");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Pagamento - Circuit Breaker com @CircuitBreaker annotation")
    class PagamentoCircuitBreakerTests {

        /**
         * Mesmo mecanismo: LoadBalancer sem serviços registrados -> ServiceUnavailable -> fallback.
         * O pagamento retorna status FALHA_TRANSITORIA (diferente de INDISPONIVEL dos outros).
         */
        @Test
        @DisplayName("deve acionar fallback quando pagamento-service não está disponível")
        void deveAcionarFallbackQuandoPagamentoServiceNaoEstaDisponivel() {
            PagamentoResponse result = integracoesService.processarPagamento(
                    "pedido-001", 140.50);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(result.transacaoId()).isNull();
        }

        /**
         * Mesma lógica: 5 falhas implícitas (serviços offline) abrem o circuit breaker de pagamento.
         */
        @Test
        @DisplayName("deve abrir circuit breaker de pagamento após 5 falhas consecutivas")
        void deveAbrirCircuitBreakerDePagamentoApos5FalhasConsecutivas() {
            for (int i = 0; i < 5; i++) {
                PagamentoResponse result = integracoesService.processarPagamento(
                        "pedido-" + i, 100.0);
                assertThat(result.status()).isEqualTo("FALHA_TRANSITORIA");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pagamento");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Fallback via @CircuitBreaker annotation - Chamada direta")
    class FallbackViaAnnotationTests {

        @Test
        @DisplayName("fallback de estoque deve retornar status FALHA_TRANSITORIA")
        void fallbackDeEstoqueDeveRetornarStatusFalhaTransitoria() {
            ReservaResponse fallback = integracoesService.reservaFallback(
                    "pedido-001", "SKU-ABC", 2,
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.reservaId()).isNull();
        }

        @Test
        @DisplayName("fallback de frete deve retornar status FALHA_TRANSITORIA")
        void fallbackDeFreteDeveRetornarStatusFalhaTransitoria() {
            FreteResponse fallback = integracoesService.freteFallback(
                    "pedido-001", "SKU-ABC", 2, "01310-100",
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.freteId()).isNull();
            assertThat(fallback.valorFrete()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("fallback de pagamento deve retornar status FALHA_TRANSITORIA")
        void fallbackDePagamentoDeveRetornarStatusFalhaTransitoria() {
            PagamentoResponse fallback = integracoesService.pagamentoFallback(
                    "pedido-001", 140.50,
                    new RuntimeException("Causa original"));

            assertThat(fallback.status()).isEqualTo("FALHA_TRANSITORIA");
            assertThat(fallback.transacaoId()).isNull();
        }
    }
}
