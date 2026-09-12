package com.example.vendas.usuario.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.usuario.application.UsuarioService;
import com.example.vendas.usuario.domain.model.Usuario;
import com.example.vendas.usuario.web.dto.UsuarioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;

@RestController
public class UsuarioController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    private final UsuarioService usuarioService;
    private final PedidoService pedidoService;

    public UsuarioController(UsuarioService usuarioService, PedidoService pedidoService) {
        this.usuarioService = usuarioService;
        this.pedidoService = pedidoService;
    }

    @GetMapping("/usuario/pedidos")
    public ResponseEntity<?> listarPedidosDoUsuario(@AuthenticationPrincipal Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        log.info("Listagem de pedidos do usuario recebida (login={})", login);

        Usuario usuario = usuarioService.buscarPorLogin(login).orElse(null);
        if (usuario == null) {
            log.warn("Usuario nao encontrado (login={})", login);
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "error", "Not Found",
                    "message", "Usuario nao encontrado: " + login));
        }

        List<PedidoResponse> pedidos = pedidoService.buscarTodos().stream()
                .filter(p -> usuario.getId().equals(p.usuarioId()))
                .toList();

        log.info("Retornando {} pedidos para o usuario (login={}, usuarioId={})", pedidos.size(), login, usuario.getId());
        return ResponseEntity.ok(pedidos);
    }
}
