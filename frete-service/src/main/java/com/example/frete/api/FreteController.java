package com.example.frete.api;

import com.example.frete.core.FreteCore;
import com.example.frete.core.dto.FreteRequest;
import com.example.frete.core.dto.FreteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class FreteController {

    private static final Logger log = LoggerFactory.getLogger(FreteController.class);

    private final FreteCore frete;

    @Value("${frete.failRate:0.2}")
    private double failRate;

    @Value("${frete.delayMs:0}")
    private long delayMs;

    public FreteController(FreteCore frete) {
        this.frete = frete;
    }

    @PostMapping("/frete/calcular")
    public FreteResponse calcular(@RequestBody FreteRequest request) {
        log.info("Requisicao de frete recebida (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                request != null ? request.pedidoId() : null,
                request != null ? request.sku() : null,
                request != null ? request.quantidade() : null,
                request != null ? request.cepDestino() : null);

        if (request == null || request.pedidoId() == null || request.pedidoId().isBlank()) {
            log.warn("Requisicao de frete invalida: pedidoId obrigatorio");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pedidoId obrigatorio");
        }
        if (request.quantidade() <= 0) {
            log.warn("Requisicao de frete invalida: quantidade menor ou igual a zero (pedidoId={}, quantidade={})",
                    request.pedidoId(), request.quantidade());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantidade deve ser > 0");
        }
        if (request.cepDestino() == null || request.cepDestino().isBlank()) {
            log.warn("Requisicao de frete invalida: cepDestino obrigatorio");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cepDestino obrigatorio");
        }

        // Simula latencia/falhas para estudar timeouts + circuit breaker.
        if (delayMs > 0) {
            log.info("Simulando latencia de frete (pedidoId={}, delayMs={})", request.pedidoId(), delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Latencia simulada interrompida (pedidoId={})", request.pedidoId());
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < failRate) {
            log.warn("Falha simulada no frete (pedidoId={}, failRate={})", request.pedidoId(), failRate);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falha simulada no servico de frete");
        }

        try {
            FreteResponse response = frete.calcular(request);
            log.info("Frete calculado com sucesso (freteId={}, valorFrete={}, prazoEntrega={})",
                    response.freteId(), response.valorFrete(), response.prazoEntrega());
            return response;
        } catch (IllegalArgumentException e) {
            log.warn("Falha de validacao ao calcular frete: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/frete/calcular/{freteId}")
    public void cancelar(@PathVariable String freteId) {
        log.info("Solicitacao de cancelamento de frete recebida (freteId={})", freteId);
        boolean ok = frete.cancelar(freteId);
        if (!ok) {
            log.warn("Frete nao encontrado para cancelamento (freteId={})", freteId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Frete nao encontrado: " + freteId);
        }
        log.info("Frete cancelado com sucesso (freteId={})", freteId);
    }

    @PostMapping("/frete/calcular/{freteId}/cancelar")
    public void cancelarPorPost(@PathVariable String freteId) {
        log.info("Solicitacao de cancelamento de frete via POST recebida (freteId={})", freteId);
        boolean ok = frete.cancelar(freteId);
        if (!ok) {
            log.warn("Frete nao encontrado para cancelamento (freteId={})", freteId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Frete nao encontrado: " + freteId);
        }
        log.info("Frete cancelado com sucesso (freteId={})", freteId);
    }

    @PostMapping("/whoami")
    public Map<String, Object> whoami() {
        return Map.of(
                "service", "frete-service",
                "port", System.getProperty("server.port", "8084"),
                "instanceId", UUID.randomUUID().toString()
        );
    }
}
