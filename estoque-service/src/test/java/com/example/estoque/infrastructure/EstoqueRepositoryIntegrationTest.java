package com.example.estoque.infrastructure;

import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.model.ReservaEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import com.example.estoque.domain.port.ReservaEstoqueRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EstoqueRepositoryIntegrationTest {

    @Autowired
    private ItemEstoqueRepositoryPort itemRepository;

    @Autowired
    private ReservaEstoqueRepositoryPort reservaRepository;

    @Autowired
    private com.example.estoque.infrastructure.repository.ItemEstoqueJpaRepository itemJpaRepository;

    @BeforeEach
    void setUp() {
        itemJpaRepository.deleteAll();
        itemRepository.salvar(new ItemEstoque("ABC-123", "Teclado Mecanico", 42));
        itemRepository.salvar(new ItemEstoque("XYZ-789", "Mouse Gamer", 15));
    }

    @Nested
    @DisplayName("ItemEstoqueRepository")
    class ItemEstoqueRepositoryTests {

        @Test
        @DisplayName("deve salvar e buscar item por SKU")
        void deveSalvarEBuscarItemPorSku() {
            Optional<ItemEstoque> encontrado = itemRepository.buscarPorSku("ABC-123");

            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().getSku()).isEqualTo("ABC-123");
            assertThat(encontrado.get().getDescricao()).isEqualTo("Teclado Mecanico");
            assertThat(encontrado.get().getQuantidade()).isEqualTo(42);
        }

        @Test
        @DisplayName("deve listar todos os itens")
        void deveListarTodosOsItens() {
            List<ItemEstoque> itens = itemRepository.listarTodos();

            assertThat(itens).hasSize(2);
            assertThat(itens).extracting(ItemEstoque::getSku)
                    .containsExactlyInAnyOrder("ABC-123", "XYZ-789");
        }

        @Test
        @DisplayName("deve atualizar quantidade do item")
        void deveAtualizarQuantidadeDoItem() {
            ItemEstoque item = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            item.decrementar(10);
            itemRepository.salvar(item);

            ItemEstoque atualizado = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            assertThat(atualizado.getQuantidade()).isEqualTo(32);
        }

        @Test
        @DisplayName("deve retornar vazio para SKU inexistente")
        void deveRetornarVaziaoParaSkuInexistente() {
            Optional<ItemEstoque> encontrado = itemRepository.buscarPorSku("INEXISTENTE");

            assertThat(encontrado).isEmpty();
        }
    }

    @Nested
    @DisplayName("ReservaEstoqueRepository")
    class ReservaEstoqueRepositoryTests {

        @Test
        @DisplayName("deve salvar e buscar reserva por ID")
        void deveSalvarEBuscarReservaPorId() {
            ReservaEstoque reserva = new ReservaEstoque("reserva-001", "ABC-123", 5, "pedido-001");
            reservaRepository.salvar(reserva);

            Optional<ReservaEstoque> encontrada = reservaRepository.buscarPorId("reserva-001");

            assertThat(encontrada).isPresent();
            assertThat(encontrada.get().getSku()).isEqualTo("ABC-123");
            assertThat(encontrada.get().getQuantidade()).isEqualTo(5);
            assertThat(encontrada.get().getPedidoId()).isEqualTo("pedido-001");
        }

        @Test
        @DisplayName("deve remover reserva por ID")
        void deveRemoverReservaPorId() {
            ReservaEstoque reserva = new ReservaEstoque("reserva-001", "ABC-123", 5, "pedido-001");
            reservaRepository.salvar(reserva);

            reservaRepository.removerPorId("reserva-001");

            Optional<ReservaEstoque> encontrada = reservaRepository.buscarPorId("reserva-001");
            assertThat(encontrada).isEmpty();
        }

        @Test
        @DisplayName("deve retornar vazio para reserva inexistente")
        void deveRetornarVaziaoParaReservaInexistente() {
            Optional<ReservaEstoque> encontrada = reservaRepository.buscarPorId("reserva-inexistente");

            assertThat(encontrada).isEmpty();
        }
    }
}
