package com.example.vendas.integration;

import com.example.vendas.integration.dto.ReservaRequest;
import com.example.vendas.integration.dto.ReservaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign para integração com o estoque-service.
 */
@FeignClient(name = "estoque-service")
public interface EstoqueClient {

    /**
     * Cria uma reserva de estoque.
     *
     * @param request dados da reserva a ser criada
     * @return resposta da reserva criada
     */
    @PostMapping("/estoque/reservas")
    ReservaResponse reservar(@RequestBody ReservaRequest request);

    /**
     * Cancela uma reserva de estoque.
     *
     * @param reservaId identificador da reserva a ser cancelada
     */
    @DeleteMapping("/estoque/reservas/{reservaId}")
    void cancelarReserva(@PathVariable String reservaId);
}

