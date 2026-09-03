package com.example.estoque.infrastructure;

import com.example.estoque.application.EstoqueService;
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
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.example.estoque.config.TestSecurityConfig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestSecurityConfig.class)
@Transactional
class EstoqueRepositoryIntegrationTest {

    @Autowired
    private ItemEstoqueRepositoryPort itemRepository;

    @Autowired
    private ReservaEstoqueRepositoryPort reservaRepository;

    @Autowired
    private com.example.estoque.infrastructure.repository.ItemEstoqueJpaRepository itemJpaRepository;

    @Autowired
    private com.example.estoque.infrastructure.repository.ReservaEstoqueJpaRepository reservaJpaRepository;

    @Autowired
    private EstoqueService estoqueService;

    @BeforeEach
    void setUp() {
        reservaJpaRepository.deleteAll();
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

        @Test
        @DisplayName("deve detectar reserva existente por pedidoId e sku")
        void deveDetectarReservaExistentePorPedidoIdESku() {
            ReservaEstoque reserva = new ReservaEstoque("reserva-001", "ABC-123", 5, "pedido-001");
            reservaRepository.salvar(reserva);

            boolean existe = reservaRepository.existsByPedidoIdAndSku("pedido-001", "ABC-123");

            assertThat(existe).isTrue();
        }

        @Test
        @DisplayName("deve retornar falso quando nao existe reserva para pedidoId e sku")
        void deveRetornarFalsoQuandoNaoExisteReservaParaPedidoIdESku() {
            boolean existe = reservaRepository.existsByPedidoIdAndSku("pedido-inexistente", "ABC-123");

            assertThat(existe).isFalse();
        }

        @Test
        @DisplayName("deve buscar reserva por pedidoId e sku")
        void deveBuscarReservaPorPedidoIdESku() {
            ReservaEstoque reserva = new ReservaEstoque("reserva-002", "XYZ-789", 3, "pedido-002");
            reservaRepository.salvar(reserva);

            Optional<ReservaEstoque> encontrada = reservaRepository.buscarPorPedidoIdESku("pedido-002", "XYZ-789");

            assertThat(encontrada).isPresent();
            assertThat(encontrada.get().getId()).isEqualTo("reserva-002");
            assertThat(encontrada.get().getSku()).isEqualTo("XYZ-789");
            assertThat(encontrada.get().getQuantidade()).isEqualTo(3);
            assertThat(encontrada.get().getPedidoId()).isEqualTo("pedido-002");
        }

        @Test
        @DisplayName("deve retornar vazio ao buscar reserva por pedidoId e sku inexistentes")
        void deveRetornarVazioAoBuscarReservaPorPedidoIdESkuInexistentes() {
            Optional<ReservaEstoque> encontrada = reservaRepository.buscarPorPedidoIdESku("nao-existe", "NAO-EXISTE");

            assertThat(encontrada).isEmpty();
        }
    }

    @Nested
    @DisplayName("EstoqueService - Idempotencia (integracao)")
    class EstoqueServiceIdempotenciaTests {

        @Test
        @DisplayName("deve criar reserva e retornar mesma reserva ao chamar duas vezes com mesmo pedidoId e sku")
        void deveCriarReservaERetornarMesmaReservaAoChamarDuasVezesComMesmoPedidoIdESku() {
            ReservaEstoque reserva1 = estoqueService.reservar("pedido-integ-001", "ABC-123", 2);

            assertThat(reserva1).isNotNull();
            assertThat(reserva1.getPedidoId()).isEqualTo("pedido-integ-001");
            assertThat(reserva1.getSku()).isEqualTo("ABC-123");
            assertThat(reserva1.getQuantidade()).isEqualTo(2);

            ReservaEstoque reserva2 = estoqueService.reservar("pedido-integ-001", "ABC-123", 2);

            assertThat(reserva2).isNotNull();
            assertThat(reserva2.getId()).isEqualTo(reserva1.getId());
            assertThat(reserva2.getPedidoId()).isEqualTo("pedido-integ-001");
            assertThat(reserva2.getSku()).isEqualTo("ABC-123");

            ItemEstoque item = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            assertThat(item.getQuantidade()).isEqualTo(40); // 42 - 2 (decrementado apenas uma vez)
        }

        @Test
        @DisplayName("deve permitir reservas diferentes para mesmo pedidoId com skus diferentes")
        void devePermitirReservasDiferentesParaMesmoPedidoIdComSkusDiferentes() {
            ReservaEstoque reserva1 = estoqueService.reservar("pedido-multi-sku", "ABC-123", 2);
            ReservaEstoque reserva2 = estoqueService.reservar("pedido-multi-sku", "XYZ-789", 1);

            assertThat(reserva1.getId()).isNotEqualTo(reserva2.getId());
            assertThat(reserva1.getSku()).isEqualTo("ABC-123");
            assertThat(reserva2.getSku()).isEqualTo("XYZ-789");

            ItemEstoque itemAbc = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            ItemEstoque itemXyz = itemRepository.buscarPorSku("XYZ-789").orElseThrow();
            assertThat(itemAbc.getQuantidade()).isEqualTo(40); // 42 - 2
            assertThat(itemXyz.getQuantidade()).isEqualTo(14); // 15 - 1
        }

        @Test
        @DisplayName("deve decrementar estoque corretamente em reservas idempotentes")
        void deveDecrementarEstoqueCorretamenteEmReservasIdempotentes() {
            ItemEstoque itemAntes = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            int qtdAntes = itemAntes.getQuantidade();

            estoqueService.reservar("pedido-dec-001", "ABC-123", 3);
            estoqueService.reservar("pedido-dec-001", "ABC-123", 3);

            ItemEstoque itemDepois = itemRepository.buscarPorSku("ABC-123").orElseThrow();
            assertThat(itemDepois.getQuantidade()).isEqualTo(qtdAntes - 3); // decrementado apenas uma vez
        }
    }
}
