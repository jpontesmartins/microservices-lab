package com.example.estoque.api;

import com.example.estoque.core.EstoqueCore;
import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueControllerTest {

    @Mock
    private EstoqueCore estoqueCore;

    @InjectMocks
    private EstoqueController estoqueController;

    private ReservaResponse reservaResponse;

    @BeforeEach
    void setUp() {
        reservaResponse = new ReservaResponse("reserva-001", "RESERVADO", "ABC-123", 5, "pedido-001");
    }

    @Nested
    @DisplayName("listarItens()")
    class ListarItensTests {

        @Test
        @DisplayName("deve listar itens com sucesso no endpoint legado")
        void deveListarItensComSucessoNoEndpointLegado() {
            List<ItemEstoqueResponse> itens = List.of(
                    new ItemEstoqueResponse("ABC-123", "Teclado Mecanico", 42));
            when(estoqueCore.listarItens()).thenReturn(itens);

            List<ItemEstoqueResponse> result = estoqueController.listarItens();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).sku()).isEqualTo("ABC-123");
        }

        @Test
        @DisplayName("deve listar itens com sucesso no endpoint /estoque/itens")
        void deveListarItensComSucessoNoEndpointComPrefixo() {
            List<ItemEstoqueResponse> itens = List.of(
                    new ItemEstoqueResponse("ABC-123", "Teclado Mecanico", 42),
                    new ItemEstoqueResponse("XYZ-789", "Mouse Gamer", 15));
            when(estoqueCore.listarItens()).thenReturn(itens);

            List<ItemEstoqueResponse> result = estoqueController.listarItensComPrefixo();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("reservar()")
    class ReservarTests {

        @Test
        @DisplayName("deve reservar estoque com sucesso")
        void deveReservarEstoqueComSucesso() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 5);
            when(estoqueCore.reservar(any(ReservaRequest.class))).thenReturn(reservaResponse);

            ReservaResponse result = estoqueController.reservar(request);

            assertThat(result).isNotNull();
            assertThat(result.reservaId()).isEqualTo("reserva-001");
            assertThat(result.status()).isEqualTo("RESERVADO");
        }

        @Test
        @DisplayName("deve retornar 409 quando estoque e insuficiente")
        void deveRetornar409QuandoEstoqueEInsuficiente() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 100);
            when(estoqueCore.reservar(any(ReservaRequest.class)))
                    .thenThrow(new IllegalStateException("Sem estoque para SKU ABC-123"));

            assertThatThrownBy(() -> estoqueController.reservar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }

        @Test
        @DisplayName("deve retornar 400 quando validacao falha")
        void deveRetornar400QuandoValidacaoFalha() {
            ReservaRequest request = new ReservaRequest("pedido-001", "", 1);
            when(estoqueCore.reservar(any(ReservaRequest.class)))
                    .thenThrow(new IllegalArgumentException("sku obrigatorio"));

            assertThatThrownBy(() -> estoqueController.reservar(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
    }

    @Nested
    @DisplayName("cancelarReserva()")
    class CancelarReservaTests {

        @Test
        @DisplayName("deve cancelar reserva com sucesso")
        void deveCancelarReservaComSucesso() {
            when(estoqueCore.cancelarReserva("reserva-001")).thenReturn(true);

            estoqueController.cancelarReserva("reserva-001");

            verify(estoqueCore).cancelarReserva("reserva-001");
        }

        @Test
        @DisplayName("deve retornar 404 quando reserva nao e encontrada")
        void deveRetornar404QuandoReservaNaoEEncontrada() {
            when(estoqueCore.cancelarReserva("reserva-inexistente")).thenReturn(false);

            assertThatThrownBy(() -> estoqueController.cancelarReserva("reserva-inexistente"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rse.getReason()).isEqualTo("Reserva nao encontrada: reserva-inexistente");
                    });
        }
    }
}
