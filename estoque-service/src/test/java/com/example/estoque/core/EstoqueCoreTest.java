package com.example.estoque.core;

import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstoqueCoreTest {

    private EstoqueCore estoqueCore;

    @BeforeEach
    void setUp() {
        estoqueCore = new EstoqueCore();
    }

    @Nested
    @DisplayName("listarItens()")
    class ListarItensTests {

        @Test
        @DisplayName("deve listar itens seeded no construtor")
        void deveListarItensSeededNoConstrutor() {
            List<ItemEstoqueResponse> itens = estoqueCore.listarItens();

            assertThat(itens).hasSize(2);
            assertThat(itens).extracting(ItemEstoqueResponse::sku)
                    .containsExactlyInAnyOrder("ABC-123", "XYZ-789");
        }

        @Test
        @DisplayName("deve retornar quantidade correta dos itens")
        void deveRetornarQuantidadeCorretaDosItens() {
            List<ItemEstoqueResponse> itens = estoqueCore.listarItens();

            ItemEstoqueResponse teclado = itens.stream()
                    .filter(i -> i.sku().equals("ABC-123"))
                    .findFirst().orElseThrow();
            ItemEstoqueResponse mouse = itens.stream()
                    .filter(i -> i.sku().equals("XYZ-789"))
                    .findFirst().orElseThrow();

            assertThat(teclado.quantidade()).isEqualTo(42);
            assertThat(mouse.quantidade()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("reservar()")
    class ReservarTests {

        @Test
        @DisplayName("deve reservar estoque com sucesso")
        void deveReservarEstoqueComSucesso() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 5);

            ReservaResponse response = estoqueCore.reservar(request);

            assertThat(response).isNotNull();
            assertThat(response.reservaId()).isNotBlank();
            assertThat(response.status()).isEqualTo("RESERVADO");
            assertThat(response.sku()).isEqualTo("ABC-123");
            assertThat(response.quantidade()).isEqualTo(5);
            assertThat(response.pedidoId()).isEqualTo("pedido-001");
        }

        @Test
        @DisplayName("deve decrementar estoque apos reserva")
        void deveDecrementarEstoqueAposReserva() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 10);

            estoqueCore.reservar(request);

            List<ItemEstoqueResponse> itens = estoqueCore.listarItens();
            ItemEstoqueResponse teclado = itens.stream()
                    .filter(i -> i.sku().equals("ABC-123"))
                    .findFirst().orElseThrow();
            assertThat(teclado.quantidade()).isEqualTo(32); // 42 - 10
        }

        @Test
        @DisplayName("deve lancar excecao quando request e null")
        void deveLancarExcecaoQuandoRequestENull() {
            assertThatThrownBy(() -> estoqueCore.reservar(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Request nao pode ser null");
        }

        @Test
        @DisplayName("deve lancar excecao quando sku e vazio")
        void deveLancarExcecaoQuandoSkuEVazio() {
            ReservaRequest request = new ReservaRequest("pedido-001", "", 1);

            assertThatThrownBy(() -> estoqueCore.reservar(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sku obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando quantidade e menor ou igual a zero")
        void deveLancarExcecaoQuandoQuantidadeEMenorOuIgualAZero() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 0);

            assertThatThrownBy(() -> estoqueCore.reservar(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("quantidade deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando SKU e desconhecido")
        void deveLancarExcecaoQuandoSkuEDesconhecido() {
            ReservaRequest request = new ReservaRequest("pedido-001", "SKU-INVALIDO", 1);

            assertThatThrownBy(() -> estoqueCore.reservar(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SKU desconhecido");
        }

        @Test
        @DisplayName("deve lancar excecao quando estoque e insuficiente")
        void deveLancarExcecaoQuandoEstoqueEInsuficiente() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 100);

            assertThatThrownBy(() -> estoqueCore.reservar(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Sem estoque para SKU");
        }

        @Test
        @DisplayName("deve gerar reservaId unico para cada reserva")
        void deveGerarReservaIdUnicoParaCadaReserva() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 1);

            ReservaResponse r1 = estoqueCore.reservar(request);
            ReservaResponse r2 = estoqueCore.reservar(request);

            assertThat(r1.reservaId()).isNotEqualTo(r2.reservaId());
        }
    }

    @Nested
    @DisplayName("cancelarReserva()")
    class CancelarReservaTests {

        @Test
        @DisplayName("deve cancelar reserva e restaurar estoque")
        void deveCancelarReservaERestaurarEstoque() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 10);
            ReservaResponse reserva = estoqueCore.reservar(request);

            boolean ok = estoqueCore.cancelarReserva(reserva.reservaId());

            assertThat(ok).isTrue();
            List<ItemEstoqueResponse> itens = estoqueCore.listarItens();
            ItemEstoqueResponse teclado = itens.stream()
                    .filter(i -> i.sku().equals("ABC-123"))
                    .findFirst().orElseThrow();
            assertThat(teclado.quantidade()).isEqualTo(42); // restaurado
        }

        @Test
        @DisplayName("deve retornar false ao cancelar reserva inexistente")
        void deveRetornarFalseAoCancelarReservaInexistente() {
            boolean ok = estoqueCore.cancelarReserva("reserva-inexistente");

            assertThat(ok).isFalse();
        }

        @Test
        @DisplayName("deve cancelar apenas a reserva especificada")
        void deveCancelarApenasAReservaEspecifica() {
            ReservaRequest request = new ReservaRequest("pedido-001", "ABC-123", 5);
            ReservaResponse r1 = estoqueCore.reservar(request);
            ReservaResponse r2 = estoqueCore.reservar(request);

            estoqueCore.cancelarReserva(r1.reservaId());

            // r2 ainda existe
            boolean ok2 = estoqueCore.cancelarReserva(r2.reservaId());
            assertThat(ok2).isTrue();
        }
    }
}
