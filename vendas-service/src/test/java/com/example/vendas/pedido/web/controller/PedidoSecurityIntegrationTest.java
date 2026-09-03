package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @DisplayName("deve criar pedido com Idempotency-Key e retornar 200")
    @WithMockUser(roles = "USER")
    void deveCriarPedidoComIdempotencyKeyERetornar200() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(buildMockJwt());

        PedidoResponse response = new PedidoResponse(
                "minha-chave-idemp", "PAGO", List.of(), 261.0, 20.0, "tx-001", "2026-09-01T10:00:00", null);
        when(pedidos.criarPedido(any(CriarPedidoRequest.class), eq("minha-chave-idemp")))
                .thenReturn(response);

        String requestJson = """
                {"items":[{"sku":"SKU-ABC","quantidade":2,"valor":120.50}],"cepDestino":"01310-100"}""";

        mockMvc.perform(post("/vendas/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "minha-chave-idemp")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value("minha-chave-idemp"))
                .andExpect(jsonPath("$.status").value("PAGO"));
    }

    @Test
    @DisplayName("deve criar pedido sem Idempotency-Key e retornar 200")
    @WithMockUser(roles = "USER")
    void deveCriarPedidoSemIdempotencyKeyERetornar200() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(buildMockJwt());

        PedidoResponse response = new PedidoResponse(
                "uuid-auto", "PAGO", List.of(), 261.0, 20.0, "tx-002", "2026-09-01T10:00:00", null);
        when(pedidos.criarPedido(any(CriarPedidoRequest.class), isNull()))
                .thenReturn(response);

        String requestJson = """
                {"items":[{"sku":"SKU-ABC","quantidade":2,"valor":120.50}],"cepDestino":"01310-100"}""";

        mockMvc.perform(post("/vendas/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value("uuid-auto"));
    }

    @Test
    @DisplayName("deve retornar pedido existente quando Idempotency-Key ja utilizada")
    @WithMockUser(roles = "USER")
    void deveRetornarPedidoExistenteQuandoIdempotencyKeyJaUtilizada() throws Exception {
        when(jwtDecoder.decode(any(String.class))).thenReturn(buildMockJwt());

        PedidoResponse response = new PedidoResponse(
                "chave-duplicada", "FALHA_ESTOQUE", List.of(), 50.0, 0.0, null, "2026-09-01T10:00:00", "SKU desconhecido");
        when(pedidos.criarPedido(any(CriarPedidoRequest.class), eq("chave-duplicada")))
                .thenReturn(response);

        String requestJson = """
                {"items":[{"sku":"SKU-INEXISTENTE","quantidade":1,"valor":50.00}],"cepDestino":"01310-100"}""";

        mockMvc.perform(post("/vendas/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "chave-duplicada")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value("chave-duplicada"))
                .andExpect(jsonPath("$.status").value("FALHA_ESTOQUE"));
    }

    @Test
    @DisplayName("deve retornar 400 quando Idempotency-Key contem caracteres invalidos")
    @WithMockUser(roles = "USER")
    void deveRetornar400QuandoIdempotencyKeyContemCaracteresInvalidos() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(buildMockJwt());
        when(pedidos.criarPedido(any(CriarPedidoRequest.class), eq("key@invalida!#")))
                .thenThrow(new IllegalArgumentException("Idempotency-Key deve conter apenas alfanumerico e hifens"));

        String requestJson = """
                {"items":[{"sku":"SKU-ABC","quantidade":1,"valor":100.00}],"cepDestino":"01310-100"}""";

        mockMvc.perform(post("/vendas/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key@invalida!#")
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
