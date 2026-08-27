package com.example.frete.core;

import com.example.frete.core.dto.FreteRequest;
import com.example.frete.core.dto.FreteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Núcleo de lógica de negócio do serviço de frete.
 * Calcula valor e prazo de entrega baseado na quantidade de itens e CEP de destino.
 */
@Service
public class FreteCore {

    private static final Logger log = LoggerFactory.getLogger(FreteCore.class);

    private final Map<String, FreteResponse> fretes = new ConcurrentHashMap<>();

    /**
     * Calcula o frete para um pedido.
     *
     * @param request dados para o calculo (pedidoId, sku, quantidade, cepDestino)
     * @return resposta com freteId, valor e prazo de entrega
     */
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

    /**
     * Cancela um frete calculado.
     *
     * @param freteId identificador do frete a ser cancelado
     * @return {@code true} se o frete foi cancelado com sucesso, {@code false} se nao encontrado
     */
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

    /**
     * Calcula o valor do frete baseado na quantidade de itens.
     * Formula: valorBase (10.0) + (quantidade * 5.0).
     *
     * @param quantidade quantidade de itens
     * @return valor total do frete
     */
    private double calcularValorFrete(int quantidade) {
        double valorBase = 10.0;
        double valorPorItem = 5.0;
        return valorBase + (quantidade * valorPorItem);
    }

    /**
     * Calcula o prazo de entrega baseado no CEP de destino.
     * Regiões: SP (3 dias), Sudeste (4 dias), Nordeste/Sul (5 dias), Norte/Centro-Oeste (7 dias).
     *
     * @param cepDestino CEP de destino
     * @return prazo estimado de entrega em dias uteis
     */
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
