package com.example.pagamento.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class PagamentoController {

    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);

    @Value("${pagamento.failRate:0.2}")
    private double failRate;

    @Value("${pagamento.delayMs:0}")
    private long delayMs;

    @GetMapping("/status")
    public Map<String, Object> status() {
        log.info("Status consultado no payment service");
        return Map.of(
                "status", "OK",
                "mensagem", "Pagamentos disponíveis",
                "provedor", "Stripe-sandbox"
        );
    }

    @GetMapping("/pagamento/status")
    public Map<String, Object> statusComPrefixo() {
        log.info("Status consultado no endpoint com prefixo /pagamento/status");
        return status();
    }

    @PostMapping("/pagamento/pagamentos")
    public PagamentoResponse pagar(@RequestBody PagamentoRequest request) {
        log.info("Requisicao de pagamento recebida (pedidoId={}, valor={})",
                request != null ? request.pedidoId() : null,
                request != null ? request.valor() : null);
        if (request == null || request.pedidoId() == null || request.pedidoId().isBlank()) {
            log.warn("Requisicao de pagamento invalida: pedidoId obrigatorio");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pedidoId obrigatorio");
        }
        if (request.valor() <= 0) {
            log.warn("Requisicao de pagamento invalida: valor menor ou igual a zero (pedidoId={}, valor={})", request.pedidoId(), request.valor());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valor deve ser > 0");
        }

        simularFalha(request.pedidoId());

        PagamentoResponse response = new PagamentoResponse(UUID.randomUUID().toString(), "APROVADO", request.pedidoId(), request.valor());
        log.info("Pagamento aprovado (pedidoId={}, transacaoId={}, valor={})", request.pedidoId(), response.transacaoId(), request.valor());
        return response;
    }

    public record PagamentoRequest(String pedidoId, double valor) {
    }

    public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
    }

    // === Estudo: Simulacao de falhas para testar Circuit Breaker ===
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
