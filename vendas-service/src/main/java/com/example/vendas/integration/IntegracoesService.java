package com.example.vendas.integration;

import com.example.vendas.integration.dto.FreteRequest;
import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoRequest;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaRequest;
import com.example.vendas.integration.dto.ReservaResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntegracoesService {

    private static final Logger log = LoggerFactory.getLogger(IntegracoesService.class);

    private final EstoqueClient estoqueClient;
    private final FreteClient freteClient;
    private final PagamentoClient pagamentoClient;

    public IntegracoesService(EstoqueClient estoqueClient, FreteClient freteClient, PagamentoClient pagamentoClient) {
        this.estoqueClient = estoqueClient;
        this.freteClient = freteClient;
        this.pagamentoClient = pagamentoClient;
    }

    /**
     * Reserva estoque no estoque-service.
     * Se o circuit breaker estiver OPEN ou a chamada falhar, aciona o fallback.
     *
     * @param pedidoId  identificador do pedido
     * @param sku       codigo do produto
     * @param quantidade quantidade a reservar
     * @return resposta da reserva de estoque
     */
    @CircuitBreaker(name = "estoque", fallbackMethod = "reservaFallback")
    public ReservaResponse reservarEstoque(String pedidoId, String sku, int quantidade) {
        log.info("Chamando estoque-service para reserva (pedidoId={}, sku={}, quantidade={})", pedidoId, sku, quantidade);
        long started = System.currentTimeMillis();
        ReservaResponse response = estoqueClient.reservar(new ReservaRequest(pedidoId, sku, quantidade));
        log.info("Resposta recebida do estoque-service (pedidoId={}, reservaId={}, status={}, duracaoMs={})",
                pedidoId,
                response != null ? response.reservaId() : null,
                response != null ? response.status() : null,
                System.currentTimeMillis() - started);
        return response;
    }

    /**
     * Fallback acionado quando o circuit breaker do estoque esta OPEN ou a chamada falhou.
     * Retorna uma resposta com status FALHA_TRANSITORIA para que o fluxo continue e possa
     * tomar uma decisao (ex.: rejeitar o pedido ou tentar novamente).
     *
     * @param pedidoId  identificador do pedido
     * @param sku       codigo do produto
     * @param quantidade quantidade a reservar
     * @param t         excecao original que causou a ativacao do fallback
     * @return resposta com status FALHA_TRANSITORIA ou BusinessException se erro de negocio
     */
    @SuppressWarnings("unused")
    public ReservaResponse reservaFallback(String pedidoId, String sku, int quantidade, Throwable t) {
        log.warn("Fallback de estoque acionado (pedidoId={}, sku={}, quantidade={}, causa={})",
                pedidoId, sku, quantidade, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_ESTOQUE", t);
        }
        return new ReservaResponse(null, "FALHA_TRANSITORIA", sku, quantidade, pedidoId);
    }

    /**
     * Calcula frete no frete-service.
     * Se o circuit breaker estiver OPEN ou a chamada falhar, aciona o fallback.
     * O frete e obrigatorio para prosseguir com o pagamento.
     *
     * @param pedidoId   identificador do pedido
     * @param sku        codigo do produto
     * @param quantidade quantidade do produto
     * @param cepDestino CEP de destino para o calculo do frete
     * @return resposta com o calculo do frete
     */
    @CircuitBreaker(name = "frete", fallbackMethod = "freteFallback")
    public FreteResponse calcularFrete(String pedidoId, String sku, int quantidade, String cepDestino) {
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
        return response;
    }

    /**
     * Fallback acionado quando o circuit breaker do frete esta OPEN ou a chamada falhou.
     * Retorna uma resposta com status FALHA_TRANSITORIA para que o PedidoCore trate como
     * falha no calculo de frete.
     *
     * @param pedidoId   identificador do pedido
     * @param sku        codigo do produto
     * @param quantidade quantidade do produto
     * @param cepDestino CEP de destino para o calculo do frete
     * @param t          excecao original que causou a ativacao do fallback
     * @return resposta com status FALHA_TRANSITORIA ou BusinessException se erro de negocio
     */
    @SuppressWarnings("unused")
    public FreteResponse freteFallback(String pedidoId, String sku, int quantidade, String cepDestino, Throwable t) {
        log.warn("Fallback de frete acionado (pedidoId={}, sku={}, quantidade={}, cepDestino={}, causa={})",
                pedidoId, sku, quantidade, cepDestino, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_FRETE", t);
        }
        return new FreteResponse(null, "FALHA_TRANSITORIA", pedidoId, 0.0, null);
    }

    /**
     * Processa pagamento no pagamento-service.
     * Se o circuit breaker estiver OPEN ou a chamada falhar, aciona o fallback.
     *
     * @param pedidoId identificador do pedido
     * @param valor    valor total a ser pago (produto + frete)
     * @return resposta do processamento do pagamento
     */
    @CircuitBreaker(name = "pagamento", fallbackMethod = "pagamentoFallback")
    public PagamentoResponse processarPagamento(String pedidoId, double valor) {
        log.info("Chamando pagamento-service (pedidoId={}, valor={})", pedidoId, valor);
        long started = System.currentTimeMillis();
        PagamentoResponse response = pagamentoClient.pagar(new PagamentoRequest(pedidoId, valor));
        log.info("Resposta recebida do pagamento-service (pedidoId={}, transacaoId={}, status={}, duracaoMs={})",
                pedidoId,
                response != null ? response.transacaoId() : null,
                response != null ? response.status() : null,
                System.currentTimeMillis() - started);
        return response;
    }

    /**
     * Fallback acionado quando o circuit breaker do pagamento esta OPEN ou a chamada falhou.
     * Retorna uma resposta com status FALHA_TRANSITORIA para que o fluxo trate como
     * falha de pagamento e acione a compensacao de estoque e frete (se aplicavel).
     *
     * @param pedidoId identificador do pedido
     * @param valor    valor total a ser pago
     * @param t        excecao original que causou a ativacao do fallback
     * @return resposta com status FALHA_TRANSITORIA ou BusinessException se erro de negocio
     */
    @SuppressWarnings("unused")
    public PagamentoResponse pagamentoFallback(String pedidoId, double valor, Throwable t) {
        log.warn("Fallback de pagamento acionado (pedidoId={}, valor={}, causa={})",
                pedidoId, valor, t != null ? t.getClass().getSimpleName() : "desconhecida");
        if (isBusinessError(t)) {
            throw new BusinessException("FALHA_PAGAMENTO", t);
        }
        return new PagamentoResponse(null, "FALHA_TRANSITORIA", pedidoId, valor);
    }

    private static boolean isBusinessError(Throwable t) {
        return t instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500;
    }

    /**
     * Cancela uma reserva de estoque de forma best-effort (melhor esforco).
     * Usado para compensacao quando o pagamento ou o calculo do frete falham apos o estoque ter sido reservado.
     * Nao propaga excecoes - falha silenciosa e apenas logada.
     *
     * @param reservaId identificador da reserva a ser cancelada
     */
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

    /**
     * Cancela um frete de forma best-effort (melhor esforco).
     * Usado para compensacao quando o pagamento falha apos o frete ter sido calculado.
     * Nao propaga excecoes - falha silenciosa e apenas logada.
     *
     * @param freteId identificador do frete a ser cancelado
     */
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
