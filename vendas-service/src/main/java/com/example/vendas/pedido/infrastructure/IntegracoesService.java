package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.infrastructure.client.EstoqueClient;
import com.example.vendas.pedido.infrastructure.client.FreteClient;
import com.example.vendas.pedido.infrastructure.client.PagamentoClient;
import com.example.vendas.pedido.infrastructure.dto.FreteRequest;
import com.example.vendas.pedido.infrastructure.dto.FreteResponse;
import com.example.vendas.pedido.infrastructure.dto.PagamentoRequest;
import com.example.vendas.pedido.infrastructure.dto.PagamentoResponse;
import com.example.vendas.pedido.infrastructure.dto.ReservaRequest;
import com.example.vendas.pedido.infrastructure.dto.ReservaResponse;
import com.example.vendas.shared.exception.BusinessException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntegracoesService implements IntegracoesPort {

    private static final Logger log = LoggerFactory.getLogger(IntegracoesService.class);

    private final EstoqueClient estoqueClient;
    private final FreteClient freteClient;
    private final PagamentoClient pagamentoClient;

    public IntegracoesService(EstoqueClient estoqueClient, FreteClient freteClient, PagamentoClient pagamentoClient) {
        this.estoqueClient = estoqueClient;
        this.freteClient = freteClient;
        this.pagamentoClient = pagamentoClient;
    }

    @Override
    @Retry(name = "estoque", fallbackMethod = "reservaFallback")
    @CircuitBreaker(name = "estoque", fallbackMethod = "reservaFallback")
    public ReservaEstoqueResult reservarEstoque(String pedidoId, String sku, int quantidade) {
        log.info("Chamando estoque-service para reserva (pedidoId={}, sku={}, quantidade={})", pedidoId, sku, quantidade);
        long started = System.currentTimeMillis();
        ReservaResponse response = estoqueClient.reservar(new ReservaRequest(pedidoId, sku, quantidade));
        log.info("Resposta recebida do estoque-service (pedidoId={}, reservaId={}, status={}, duracaoMs={})",
                pedidoId,
                response != null ? response.reservaId() : null,
                response != null ? response.status() : null,
                System.currentTimeMillis() - started);
        return new ReservaEstoqueResult(response.reservaId(), response.status());
    }

    @SuppressWarnings("unused")
    public ReservaEstoqueResult reservaFallback(String pedidoId, String sku, int quantidade, Throwable t) {
        log.warn("Fallback de estoque acionado (pedidoId={}, sku={}, quantidade={}, causa={})",
                pedidoId, sku, quantidade, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_ESTOQUE", t);
        }
        return new ReservaEstoqueResult(null, "FALHA_TRANSITORIA");
    }

    @Override
    @Retry(name = "frete", fallbackMethod = "freteFallback")
    @CircuitBreaker(name = "frete", fallbackMethod = "freteFallback")
    public FreteResult calcularFrete(String pedidoId, String sku, int quantidade, String cepDestino) {
        log.info("Chamando frete-service para calculo (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                pedidoId, sku, quantidade, cepDestino);
        long started = System.currentTimeMillis();
        FreteResponse response = freteClient.calcular(new FreteRequest(pedidoId, sku, quantidade, cepDestino));
        log.info("Resposta recebida do frete-service (pedidoId={}, freteId={}, status={}, valorFrete={}, duracaoMs={})",
                pedidoId,
                response != null ? response.freteId() : null,
                response != null ? response.status() : null,
                response != null ? response.valorFrete() : null,
                System.currentTimeMillis() - started);
        return new FreteResult(response.freteId(), response.status(), response.valorFrete(), response.prazoEntrega());
    }

    @SuppressWarnings("unused")
    public FreteResult freteFallback(String pedidoId, String sku, int quantidade, String cepDestino, Throwable t) {
        log.warn("Fallback de frete acionado (pedidoId={}, sku={}, quantidade={}, cepDestino={}, causa={})",
                pedidoId, sku, quantidade, cepDestino, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_FRETE", t);
        }
        return new FreteResult(null, "FALHA_TRANSITORIA", 0.0, null);
    }

    @Override
    @Retry(name = "pagamento", fallbackMethod = "pagamentoFallback")
    @CircuitBreaker(name = "pagamento", fallbackMethod = "pagamentoFallback")
    public PagamentoResult processarPagamento(String pedidoId, double valor) {
        log.info("Chamando pagamento-service (pedidoId={}, valor={})", pedidoId, valor);
        long started = System.currentTimeMillis();
        PagamentoResponse response = pagamentoClient.pagar(new PagamentoRequest(pedidoId, valor));
        log.info("Resposta recebida do pagamento-service (pedidoId={}, transacaoId={}, status={}, duracaoMs={})",
                pedidoId,
                response != null ? response.transacaoId() : null,
                response != null ? response.status() : null,
                System.currentTimeMillis() - started);
        return new PagamentoResult(response.transacaoId(), response.status(), response.valor());
    }

    @SuppressWarnings("unused")
    public PagamentoResult pagamentoFallback(String pedidoId, double valor, Throwable t) {
        log.warn("Fallback de pagamento acionado (pedidoId={}, valor={}, causa={})",
                pedidoId, valor, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_PAGAMENTO", t);
        }
        return new PagamentoResult(null, "FALHA_TRANSITORIA", valor);
    }

    private static boolean isBusinessError(Throwable t) {
        return t instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500;
    }

    @Override
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

    @Override
    public void cancelarFreteBestEffort(String freteId) {
        if (freteId == null || freteId.isBlank()) {
            return;
        }
        try {
            freteClient.cancelar(freteId);
        } catch (Exception e) {
            log.warn("Compensacao de frete best-effort falhou (freteId={}): {}", freteId, e.toString());
        }
    }
}
