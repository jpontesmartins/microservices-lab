package com.example.vendas.integration;

import com.example.vendas.integration.dto.PagamentoRequest;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaRequest;
import com.example.vendas.integration.dto.ReservaResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntegracoesService {

    private static final Logger log = LoggerFactory.getLogger(IntegracoesService.class);

    private final EstoqueClient estoqueClient;
    private final PagamentoClient pagamentoClient;

    public IntegracoesService(EstoqueClient estoqueClient, PagamentoClient pagamentoClient) {
        this.estoqueClient = estoqueClient;
        this.pagamentoClient = pagamentoClient;
    }

    @CircuitBreaker(name = "estoque", fallbackMethod = "reservaFallback")
    public ReservaResponse reservarEstoque(String pedidoId, String sku, int quantidade) {
        log.info("Chamando estoque-service para reserva (pedidoId={}, sku={}, quantidade={})", pedidoId, sku, quantidade);
        ReservaResponse response = null;
        long started = System.currentTimeMillis();
        try {
            response = estoqueClient.reservar(new ReservaRequest(pedidoId, sku, quantidade));
            log.info("Resposta recebida do estoque-service (pedidoId={}, reservaId={}, status={}, duracaoMs={})",
                    pedidoId,
                    response != null ? response.reservaId() : null,
                    response != null ? response.status() : null,
                    System.currentTimeMillis() - started);
            return response;
        } catch (RuntimeException e) {
            log.warn("Falha ao reservar estoque (pedidoId={}, sku={}, quantidade={}, duracaoMs={}): {}",
                    pedidoId, sku, quantidade, System.currentTimeMillis() - started, e.toString());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    public ReservaResponse reservaFallback(String pedidoId, String sku, int quantidade, Throwable t) {
        log.warn("Fallback de estoque acionado (pedidoId={}, sku={}, quantidade={}): {}", pedidoId, sku, quantidade, t.toString());
        // TODO: acho que o ideal é substituir o null por algo que faça sentido, ver o que é mais indicado fazer nos Fallback dos Circuit Breakers
        return null;
    }

    @CircuitBreaker(name = "pagamento", fallbackMethod = "pagamentoFallback")
    public PagamentoResponse processarPagamento(String pedidoId, double valor) {
        log.info("Chamando pagamento-service (pedidoId={}, valor={})", pedidoId, valor);
        long started = System.currentTimeMillis();
        try {
            PagamentoResponse response = pagamentoClient.pagar(new PagamentoRequest(pedidoId, valor));
            log.info("Resposta recebida do pagamento-service (pedidoId={}, transacaoId={}, status={}, duracaoMs={})",
                    pedidoId,
                    response != null ? response.transacaoId() : null,
                    response != null ? response.status() : null,
                    System.currentTimeMillis() - started);
            return response;
        } catch (RuntimeException e) {
            log.warn("Falha ao processar pagamento (pedidoId={}, valor={}, duracaoMs={}): {}",
                    pedidoId, valor, System.currentTimeMillis() - started, e.toString());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    public PagamentoResponse pagamentoFallback(String pedidoId, double valor, Throwable t) {
        log.warn("Fallback de pagamento acionado (pedidoId={}, valor={}): {}", pedidoId, valor, t.toString());
        return null;
    }

    public void cancelarReservaBestEffort(String reservaId) {
        if (reservaId == null || reservaId.isBlank()) {
            return;
        }
        try {
            estoqueClient.cancelarReserva(reservaId);
        } catch (Exception e) {
            log.warn("Compensacao best-effort falhou (reservaId={}): {}", reservaId, e.toString());
        }
    }
}
