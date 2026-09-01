package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.shared.exception.BusinessException;
import com.example.vendas.shared.exception.TransientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidos;

    @InjectMocks
    private PedidoController controller;

    private CriarPedidoRequest requestValido() {
        return new CriarPedidoRequest(
                List.of(new ItemPedidoRequest("SKU-ABC", 2, 120.50)),
                "01310-100");
    }

    @Test
    @DisplayName("deve retornar 200 quando pedido e criado com sucesso")
    void deveRetornar200QuandoPedidoECriadoComSucesso() {
        PedidoResponse response = new PedidoResponse(
                "pedido-001", "PAGO", List.of(), 261.0, 20.0, "transacao-001", "2026-09-01T10:00:00", null);
        when(pedidos.criarPedido(any())).thenReturn(response);

        ResponseEntity<?> result = controller.criar(requestValido());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        PedidoResponse body = (PedidoResponse) result.getBody();
        assertThat(body.status()).isEqualTo("PAGO");
        assertThat(body.pedidoId()).isEqualTo("pedido-001");
    }

    @Test
    @DisplayName("deve retornar 409 com mensagem amigavel quando estoque falha")
    void deveRetornar409ComMensagemAmigavelQuandoEstoqueFalha() {
        when(pedidos.criarPedido(any()))
                .thenThrow(new BusinessException("FALHA_ESTOQUE", "Estoque insuficiente para o SKU-ABC", null));

        ResponseEntity<?> result = controller.criar(requestValido());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body.get("message")).isEqualTo("Estoque insuficiente para o SKU-ABC");
    }

    @Test
    @DisplayName("deve retornar 409 quando frete falha com erro de negocio")
    void deveRetornar409QuandoFreteFalhaComErroDeNegocio() {
        when(pedidos.criarPedido(any()))
                .thenThrow(new BusinessException("FALHA_FRETE", "CEP de destino invalido", null));

        ResponseEntity<?> result = controller.criar(requestValido());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body.get("message")).isEqualTo("CEP de destino invalido");
    }

    @Test
    @DisplayName("deve retornar 409 quando pagamento falha com erro de negocio")
    void deveRetornar409QuandoPagamentoFalhaComErroDeNegocio() {
        when(pedidos.criarPedido(any()))
                .thenThrow(new BusinessException("FALHA_PAGAMENTO", "Cartao recusado", null));

        ResponseEntity<?> result = controller.criar(requestValido());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body.get("message")).isEqualTo("Cartao recusado");
    }

    @Test
    @DisplayName("deve retornar 503 quando servico downstream esta indisponivel")
    void deveRetornar503QuandoServicoDownstreamEstaIndisponivel() {
        when(pedidos.criarPedido(any()))
                .thenThrow(new TransientException("Servico de estoque temporariamente indisponivel", null));

        ResponseEntity<?> result = controller.criar(requestValido());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("3");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body.get("message")).isEqualTo("Servico de estoque temporariamente indisponivel");
    }

    @Test
    @DisplayName("deve retornar 400 quando request e invalido")
    void deveRetornar400QuandoRequestEInvalido() {
        when(pedidos.criarPedido(any()))
                .thenThrow(new IllegalArgumentException("Body obrigatorio"));

        ResponseEntity<?> result = controller.criar(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body.get("message")).isEqualTo("Body obrigatorio");
    }
}
