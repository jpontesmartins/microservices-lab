package com.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.time.Instant;

/**
 * Configuração de logging global do API Gateway.
 * Registra method, path, query, routeId, status e duração de cada requisição.
 */
@Configuration
public class GatewayLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingConfig.class);

    /**
     * Cria um filtro global que registra o início e fim de cada requisição passando pelo gateway.
     *
     * @return GlobalFilter com logging de requisicoes
     */
    @Bean
    public GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            Instant startedAt = Instant.now();
            String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
            String path = exchange.getRequest().getURI().getRawPath();
            String query = exchange.getRequest().getURI().getRawQuery();
            String routeId = resolveRouteId(exchange);

            log.info("Gateway recebeu requisicao (method={}, path={}, query={}, routeId={})",
                    method, path, query, routeId);

            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        HttpStatusCode status = exchange.getResponse().getStatusCode();
                        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                        log.info("Gateway finalizou requisicao (method={}, path={}, routeId={}, status={}, durationMs={}, signal={})",
                                method, path, routeId, status, durationMs, signalType);
                    });
        };
    }   

    /**
     * Resolve o ID da rota associada a requisicao atual.
     *
     * @param exchange contexto da requisicao HTTP
     * @return ID da rota ou "unknown" se nao encontrada
     */
    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }
}
