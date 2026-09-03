package com.example.pagamento.api;

import com.example.pagamento.domain.model.Transacao;
import com.example.pagamento.domain.port.TransacaoRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Controller REST para operações de pagamento.
 * Processa pagamentos de pedidos com idempotência e simulação de falhas para testes de Circuit Breaker.
 */
@RestController
public class PagamentoController {

    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);

    private final TransacaoRepositoryPort transacaoRepository;

    @Value("${pagamento.failRate:0.2}")
    private double failRate;

    @Value("${pagamento.delayMs:0}")
    private long delayMs;

    public PagamentoController(TransacaoRepositoryPort transacaoRepository) {
        this.transacaoRepository = transacaoRepository;
    }

    /**
     * Retorna o status do serviço de pagamento (endpoint legado).
     *
     * @return mapa com status, mensagem e provedor
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        log.info("Status consultado no payment service");
        return Map.of(
                "status", "OK",
                "mensagem", "Pagamentos disponíveis",
                "provedor", "Stripe-sandbox"
        );
    }

    /**
     * Retorna o status do serviço de pagamento (endpoint com prefixo).
     *
     * @return mapa com status, mensagem e provedor
     */
    @GetMapping("/pagamento/status")
    public Map<String, Object> statusComPrefixo() {
        log.info("Status consultado no endpoint com prefixo /pagamento/status");
        return status();
    }

    /**
     * Processa um pagamento para um pedido com idempotência.
     * Se já existe uma transação para o pedidoId, retorna a existente.
     *
     * @param request dados do pagamento (pedidoId, valor)
     * @return resposta do pagamento processado com transacaoId e status
     * @throws ResponseStatusException BAD_REQUEST se pedidoId ausente ou valor <= 0
     */
    @PostMapping("/pagamento/pagamentos")
    public PagamentoResponse pagar(@RequestBody PagamentoRequest request) {
        log.info("Requisição de pagamento recebida (pedidoId={}, valor={})",
                request != null ? request.pedidoId() : null,
                request != null ? request.valor() : null);
        if (request == null || request.pedidoId() == null || request.pedidoId().isBlank()) {
            log.warn("Requisição de pagamento inválida: pedidoId obrigatório");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pedidoId obrigatório");
        }
        if (request.valor() <= 0) {
            log.warn("Requisição de pagamento inválida: valor menor ou igual a zero (pedidoId={}, valor={})",
                    request.pedidoId(), request.valor());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valor deve ser > 0");
        }

        Optional<Transacao> existente = transacaoRepository.buscarPorPedidoId(request.pedidoId());
        if (existente.isPresent()) {
            log.info("Pagamento já processado, retornando existente (pedidoId={}, transacaoId={})",
                    request.pedidoId(), existente.get().getId());
            Transacao t = existente.get();
            return new PagamentoResponse(t.getId(), t.getStatus(), t.getPedidoId(), t.getValor());
        }

        simularFalha(request.pedidoId());

        String transacaoId = UUID.randomUUID().toString();
        Transacao transacao = new Transacao(transacaoId, request.pedidoId(), request.valor(),
                "APROVADO", Instant.now());
        try {
            transacaoRepository.salvar(transacao);
        } catch (DataIntegrityViolationException e) {
            log.warn("Transação duplicada detectada, retornando existente (pedidoId={})", request.pedidoId());
            Transacao t = transacaoRepository.buscarPorPedidoId(request.pedidoId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao recuperar transação existente"));
            return new PagamentoResponse(t.getId(), t.getStatus(), t.getPedidoId(), t.getValor());
        }

        log.info("Pagamento aprovado (pedidoId={}, transacaoId={}, valor={})",
                request.pedidoId(), transacaoId, request.valor());
        return new PagamentoResponse(transacaoId, "APROVADO", request.pedidoId(), request.valor());
    }

    /**
     * Request para processamento de pagamento.
     *
     * @param pedidoId identificador do pedido
     * @param valor    valor total a ser pago
     */
    public record PagamentoRequest(String pedidoId, double valor) {
    }

    /**
     * Resposta do processamento de pagamento.
     *
     * @param transacaoId identificador da transação
     * @param status      status do pagamento (APROVADO, FALHA_TRANSITORIA, etc.)
     * @param pedidoId    identificador do pedido
     * @param valor       valor processado
     */
    public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
    }

    // === Estudo: Simulação de falhas para testar Circuit Breaker ===

    /**
     * Simula falhas e latência no pagamento para fins de teste de Circuit Breaker.
     * Usa configuração via application.yml: {@code pagamento.failRate} e {@code pagamento.delayMs}.
     *
     * @param pedidoId identificador do pedido (para fins de log)
     */
    private void simularFalha(String pedidoId) {
        if (delayMs > 0) {
            log.info("Simulando latencia de pagamento (pedidoId={}, delayMs={})", pedidoId, delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Latencia simulada interrompida (pedidoId={})", pedidoId);
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < failRate) {
            log.warn("Falha simulada no pagamento (pedidoId={}, failRate={})", pedidoId, failRate);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falha simulada no provedor de pagamento");
        }
    }
}
