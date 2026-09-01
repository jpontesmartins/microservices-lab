package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.shared.exception.BusinessException;
import com.example.vendas.shared.exception.TransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PedidoController {

    private static final Logger log = LoggerFactory.getLogger(PedidoController.class);

    private final PedidoService pedidos;

    public PedidoController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping("/vendas/pedidos")
    public ResponseEntity<?> criar(@RequestBody CriarPedidoRequest request) {
        log.info("Recebida requisicao de criacao de pedido (totalItens={}, cepDestino={})",
                request != null && request.items() != null ? request.items().size() : 0,
                request != null ? request.cepDestino() : null);
        try {
            PedidoResponse response = pedidos.criarPedido(request);
            log.info("Pedido processado com sucesso (pedidoId={}, status={})", response.pedidoId(), response.status());
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.warn("Erro de negocio ao criar pedido (status={})", e.getStatus());
            return ResponseEntity.status(409).body(Map.of(
                    "status", 409,
                    "error", "Conflict",
                    "message", e.getUserMessage() != null ? e.getUserMessage() : e.getStatus()));
        } catch (TransientException e) {
            log.warn("Erro transitorio ao criar pedido: {}", e.getUserMessage());
            return ResponseEntity.status(503)
                    .header("Retry-After", "3")
                    .body(Map.of(
                            "status", 503,
                            "error", "Service Unavailable",
                            "message", e.getUserMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Falha de validacao ao criar pedido: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/vendas/pedidos/{pedidoId}")
    public ResponseEntity<?> obter(@PathVariable String pedidoId) {
        log.info("Consulta de pedido recebida (pedidoId={})", pedidoId);
        PedidoResponse resp = pedidos.buscar(pedidoId);
        if (resp == null) {
            log.warn("Pedido nao encontrado (pedidoId={})", pedidoId);
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "error", "Not Found",
                    "message", "Pedido nao encontrado: " + pedidoId));
        }
        log.info("Pedido localizado (pedidoId={}, status={})", pedidoId, resp.status());
        return ResponseEntity.ok(resp);
    }
}
