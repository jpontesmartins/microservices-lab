package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import(com.example.vendas.config.SecurityConfig.class)
class PedidoSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidos;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Jwt buildMockJwt() {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "user-001")
                .claim("user_id", "user-001")
                .claim("roles", List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("deve retornar 401 quando requisicao nao tem token")
    void deveRetornar401QuandoNaoTemToken() throws Exception {
        mockMvc.perform(get("/vendas/pedidos/123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve retornar 200 quando usuario autenticado consulta pedido existente")
    @WithMockUser(roles = "USER")
    void deveRetornar200QuandoUsuarioAutenticadoConsultaPedido() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(buildMockJwt());

        PedidoResponse response = new PedidoResponse(
                "pedido-001", "PAGO", List.of(), 250.0, 15.0, "tx-001", "2026-09-01T10:00:00", null);
        when(pedidos.buscar("pedido-001")).thenReturn(response);

        mockMvc.perform(get("/vendas/pedidos/pedido-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value("pedido-001"))
                .andExpect(jsonPath("$.status").value("PAGO"));
    }

    @Test
    @DisplayName("deve retornar 404 quando pedido nao existe")
    @WithMockUser(roles = "USER")
    void deveRetornar404QuandoPedidoNaoExiste() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(buildMockJwt());
        when(pedidos.buscar("inexistente")).thenReturn(null);

        mockMvc.perform(get("/vendas/pedidos/inexistente"))
                .andExpect(status().isNotFound());
    }
}
