package com.example.vendas.api;

import com.example.vendas.core.PedidoCore;
import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PedidoController {

    private final PedidoCore pedidos;

    public PedidoController(PedidoCore pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping("/vendas/pedidos")
    public PedidoResponse criar(@RequestBody CriarPedidoRequest request) {
        try {
            return pedidos.criarPedido(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/vendas/pedidos/{pedidoId}")
    public PedidoResponse obter(@PathVariable String pedidoId) {
        PedidoResponse resp = pedidos.buscar(pedidoId);
        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado: " + pedidoId);
        }
        return resp;
    }
}

