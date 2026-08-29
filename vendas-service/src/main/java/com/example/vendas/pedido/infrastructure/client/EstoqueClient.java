package com.example.vendas.pedido.infrastructure.client;

import com.example.vendas.pedido.infrastructure.dto.ReservaRequest;
import com.example.vendas.pedido.infrastructure.dto.ReservaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "estoque-service")
public interface EstoqueClient {

    @PostMapping("/estoque/reservas")
    ReservaResponse reservar(@RequestBody ReservaRequest request);

    @DeleteMapping("/estoque/reservas/{reservaId}")
    void cancelarReserva(@PathVariable String reservaId);
}
