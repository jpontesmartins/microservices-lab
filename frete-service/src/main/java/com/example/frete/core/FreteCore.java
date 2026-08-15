package com.example.frete.core;

import com.example.frete.core.dto.FreteRequest;
import com.example.frete.core.dto.FreteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FreteCore {

    private static final Logger log = LoggerFactory.getLogger(FreteCore.class);

    private final Map<String, FreteResponse> fretes = new ConcurrentHashMap<>();

    public FreteResponse calcular(FreteRequest request) {
        log.info("Calculando frete (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                request.pedidoId(), request.sku(), request.quantidade(), request.cepDestino());

        double valorFrete = calcularValorFrete(request.quantidade());
        String prazoEntrega = calcularPrazo(request.cepDestino());

        FreteResponse response = new FreteResponse(
                UUID.randomUUID().toString(),
                "CALCULADO",
                request.pedidoId(),
                valorFrete,
                prazoEntrega
        );

        fretes.put(response.freteId(), response);
        log.info("Frete calculado com sucesso (freteId={}, valorFrete={}, prazoEntrega={})",
                response.freteId(), response.valorFrete(), response.prazoEntrega());

        return response;
    }

    public boolean cancelar(String freteId) {
        log.info("Solicitacao de cancelamento de frete (freteId={})", freteId);
        FreteResponse frete = fretes.get(freteId);
        if (frete == null) {
            return false;
        }
        fretes.remove(freteId);
        log.info("Frete cancelado com sucesso (freteId={})", freteId);
        return true;
    }

    private double calcularValorFrete(int quantidade) {
        double valorBase = 10.0;
        double valorPorItem = 5.0;
        return valorBase + (quantidade * valorPorItem);
    }

    private String calcularPrazo(String cepDestino) {
        if (cepDestino == null || cepDestino.isBlank()) {
            return "5 dias uteis";
        }
        String prefixo = cepDestino.substring(0, Math.min(1, cepDestino.length()));
        return switch (prefixo) {
            case "0" -> "3 dias uteis";  // SP capital
            case "1", "2" -> "4 dias uteis";  // Regiao Sudeste (RJ, ES)
            case "3", "4", "5" -> "5 dias uteis";  // Regiao Nordeste
            case "8", "9" -> "5 dias uteis";  // Regiao Sul (PR, SC, RS)
            default -> "7 dias uteis";  // Regiao Norte e Centro-Oeste
        };
    }
}
