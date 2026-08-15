package com.example.frete.api;

import com.example.frete.core.FreteCore;
import com.example.frete.core.dto.FreteRequest;
import com.example.frete.core.dto.FreteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreteControllerTest {

    @Mock
    private FreteCore freteCore;

    @InjectMocks
    private FreteController freteController;

    private FreteResponse freteResponse;

    @BeforeEach
    void setUp() {
        freteResponse = new FreteResponse("frete-001", "CALCULADO", "pedido-001", 20.0, "3 dias uteis");
    }

    @Nested
    @DisplayName("calcular()")
    class CalcularTests {

        @Test
        @DisplayName("deve calcular frete com sucesso")
        void deveCalcularFreteComSucesso() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU-ABC", 2, "01310-100");
            when(freteCore.calcular(any(FreteRequest.class))).thenReturn(freteResponse);

            FreteResponse response = freteController.calcular(request);

            assertThat(response).isNotNull();
            assertThat(response.freteId()).isEqualTo("frete-001");
            assertThat(response.status()).isEqualTo("CALCULADO");
            assertThat(response.valorFrete()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("deve retornar 400 quando request e null")
        void deveRetornar400QuandoRequestENull() {
            assertThatThrownBy(() -> freteController.calcular(null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatorio");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando pedidoId e vazio")
        void deveRetornar400QuandoPedidoIdEVazio() {
            FreteRequest request = new FreteRequest("", "SKU", 1, "01310-100");

            assertThatThrownBy(() -> freteController.calcular(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("pedidoId obrigatorio");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando quantidade e menor ou igual a zero")
        void deveRetornar400QuandoQuantidadeEMenorOuIgualAZero() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU", 0, "01310-100");

            assertThatThrownBy(() -> freteController.calcular(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("quantidade deve ser > 0");
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando cepDestino e vazio")
        void deveRetornar400QuandoCepDestinoEVazio() {
            FreteRequest request = new FreteRequest("pedido-001", "SKU", 1, "");

            assertThatThrownBy(() -> freteController.calcular(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("cepDestino obrigatorio");
                    });
        }
    }

    @Nested
    @DisplayName("cancelar()")
    class CancelarTests {

        @Test
        @DisplayName("deve cancelar frete com sucesso")
        void deveCancelarFreteComSucesso() {
            when(freteCore.cancelar("frete-001")).thenReturn(true);

            freteController.cancelar("frete-001");
        }

        @Test
        @DisplayName("deve retornar 404 quando frete nao e encontrado")
        void deveRetornar404QuandoFreteNaoEEncontrado() {
            when(freteCore.cancelar("frete-inexistente")).thenReturn(false);

            assertThatThrownBy(() -> freteController.cancelar("frete-inexistente"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rse.getReason()).isEqualTo("Frete nao encontrado: frete-inexistente");
                    });
        }
    }
}
