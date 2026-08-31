package com.example.vendas.pedido.infrastructure.RetryCircuitBreakerCombined;

import com.example.vendas.shared.exception.BusinessException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Helpers compartilhados para os testes de Retry + CircuitBreaker.
 * Espelha as configuracoes de {@code application.yml} do vendas-service.
 */
final class Resilience4jTestHelper {

    private Resilience4jTestHelper() {
    }

    // ========================= CircuitBreaker =========================

    /**
     * Configuracao padrao que espelha o application.yml:
     * COUNT_BASED(10), minCalls=5, failureThreshold=50%, slowCallRate=60%,
     * slowCallDuration=2s, waitOpen=10s, halfOpenPermitted=3,
     * ignoreExceptions=[BusinessException].
     */
    static CircuitBreakerConfig buildCBConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(BusinessException.class)
                .build();
    }

    /**
     * Configuracao com waitDuration curto (500ms) para testes de recovery
     * que precisam transicionar de OPEN para HALF_OPEN rapidamente.
     */
    static CircuitBreakerConfig buildCBConfigWithShortWait() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(500))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(BusinessException.class)
                .build();
    }

    // ========================= Retry =========================

    /**
     * Configuracao padrao de retry: maxAttempts=3, waitDuration=100ms,
     * retryExceptions=[IOException, TimeoutException, ResourceAccessException, RuntimeException],
     * ignoreExceptions=[BusinessException].
     *
     * <p>Inclui {@code RuntimeException} porque os helpers de teste
     * ({@link #ioFail()}, etc.) lancam RuntimeException wrapping a excecao checked.
     * Em producao, o Retry_Decorated_Supplier recebe a excecao real do Feign Client.</p>
     */
    static RetryConfig buildRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(IOException.class, TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class,
                        RuntimeException.class)
                .ignoreExceptions(BusinessException.class)
                .build();
    }

    /**
     * Configuracao de retry com maxAttempts customizavel.
     */
    static RetryConfig buildRetryConfigWithMax(int maxAttempts) {
        return RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(IOException.class, TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class,
                        RuntimeException.class)
                .ignoreExceptions(BusinessException.class)
                .build();
    }

    // ========================= Excecoes simuladas =========================

    static void ioFail() {
        throw new RuntimeException(new IOException("Falha de conexao"));
    }

    static void ioFail(String msg) {
        throw new RuntimeException(new IOException(msg));
    }

    static void timeoutFail() {
        throw new RuntimeException(new TimeoutException("Timeout"));
    }

    static void resourceAccessFail() {
        throw new RuntimeException(new org.springframework.web.client.ResourceAccessException("Offline"));
    }

    // ========================= FeignException =========================

    static FeignException feignExceptionWithStatus(int status) {
        return FeignException.errorStatus("test",
                feign.Response.builder()
                        .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://test",
                                Map.of(), null, StandardCharsets.UTF_8, new feign.RequestTemplate()))
                        .status(status)
                        .reason("Error " + status)
                        .body("", StandardCharsets.UTF_8)
                        .build());
    }
}
