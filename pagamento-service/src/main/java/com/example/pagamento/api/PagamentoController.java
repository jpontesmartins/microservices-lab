package com.example.pagamento.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    @Value("${pagamento.failRate:0.2}")
    private double failRate;

    @Value("${pagamento.delayMs:0}")
    private long delayMs;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "OK",
                "mensagem", "Pagamentos disponíveis",
                "provedor", "Stripe-sandbox"
        );
    }

    @GetMapping("/pagamento/status")
    public Map<String, Object> statusComPrefixo() {
        return status();
    }

    @PostMapping("/pagamento/pagamentos")
    public PagamentoResponse pagar(@RequestBody PagamentoRequest request) {
        if (request == null || request.pedidoId() == null || request.pedidoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pedidoId obrigatorio");
        }
        if (request.valor() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valor deve ser > 0");
        }

        // Simula latencia/falhas para estudar timeouts + circuit breaker.
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < failRate) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falha simulada no provedor de pagamento");
        }

        return new PagamentoResponse(UUID.randomUUID().toString(), "APROVADO", request.pedidoId(), request.valor());
    }

    public record PagamentoRequest(String pedidoId, double valor) {
    }

    public record PagamentoResponse(String transacaoId, String status, String pedidoId, double valor) {
    }
}
