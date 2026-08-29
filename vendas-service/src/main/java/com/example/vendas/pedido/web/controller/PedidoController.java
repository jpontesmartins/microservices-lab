package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PedidoController {

    private static final Logger log = LoggerFactory.getLogger(PedidoController.class);

    private final PedidoService pedidos;

    public PedidoController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping("/vendas/pedidos")
    public PedidoResponse criar(@RequestBody CriarPedidoRequest request) {
        log.info("Recebida requisicao de criacao de pedido (sku={}, quantidade={}, valor={})",
                request != null ? request.sku() : null,
                request != null ? request.quantidade() : null,
                request != null ? request.valor() : null);
        try {
            PedidoResponse response = pedidos.criarPedido(request);
            log.info("Pedido processado com sucesso (pedidoId={}, status={})", response.pedidoId(), response.status());
            return response;
        } catch (IllegalArgumentException e) {
            log.warn("Falha de validacao ao criar pedido: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/vendas/pedidos/{pedidoId}")
    public PedidoResponse obter(@PathVariable String pedidoId) {
        log.info("Consulta de pedido recebida (pedidoId={})", pedidoId);
        PedidoResponse resp = pedidos.buscar(pedidoId);
        if (resp == null) {
            log.warn("Pedido nao encontrado (pedidoId={})", pedidoId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado: " + pedidoId);
        }
        log.info("Pedido localizado (pedidoId={}, status={})", pedidoId, resp.status());
        return resp;
    }
}
