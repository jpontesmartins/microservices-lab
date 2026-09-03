package com.example.estoque.application;

import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.model.ReservaEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import com.example.estoque.domain.port.ReservaEstoqueRepositoryPort;
import com.example.estoque.shared.exception.EstoqueInsuficienteException;
import com.example.estoque.shared.exception.SkuDesconhecidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock
    private ItemEstoqueRepositoryPort itemRepository;

    @Mock
    private ReservaEstoqueRepositoryPort reservaRepository;

    @InjectMocks
    private EstoqueService estoqueService;

    private ItemEstoque teclado;
    private ItemEstoque mouse;

    @BeforeEach
    void setUp() {
        teclado = new ItemEstoque("ABC-123", "Teclado Mecanico", 42);
        mouse = new ItemEstoque("XYZ-789", "Mouse Gamer", 15);
    }

    @Nested
    @DisplayName("listarItens()")
    class ListarItensTests {

        @Test
        @DisplayName("deve listar todos os itens do repositorio")
        void deveListarTodosOsItensDoRepositorio() {
            when(itemRepository.listarTodos()).thenReturn(List.of(teclado, mouse));

            List<ItemEstoque> itens = estoqueService.listarItens();

            assertThat(itens).hasSize(2);
            assertThat(itens).extracting(ItemEstoque::getSku)
                    .containsExactlyInAnyOrder("ABC-123", "XYZ-789");
        }

        @Test
        @DisplayName("deve retornar lista vazia quando nao ha itens")
        void deveRetornarListaVaziaQuandoNaoHaItens() {
            when(itemRepository.listarTodos()).thenReturn(List.of());

            List<ItemEstoque> itens = estoqueService.listarItens();

            assertThat(itens).isEmpty();
        }
    }

    @Nested
    @DisplayName("reservar()")
    class ReservarTests {

        @Test
        @DisplayName("deve reservar estoque com sucesso")
        void deveReservarEstoqueComSucesso() {
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));
            when(itemRepository.salvar(any(ItemEstoque.class))).thenReturn(teclado);
            when(reservaRepository.salvar(any(ReservaEstoque.class))).thenAnswer(inv -> inv.getArgument(0));

            ReservaEstoque reserva = estoqueService.reservar("pedido-001", "ABC-123", 5);

            assertThat(reserva).isNotNull();
            assertThat(reserva.getId()).isNotBlank();
            assertThat(reserva.getSku()).isEqualTo("ABC-123");
            assertThat(reserva.getQuantidade()).isEqualTo(5);
            assertThat(reserva.getPedidoId()).isEqualTo("pedido-001");
            assertThat(teclado.getQuantidade()).isEqualTo(37); // 42 - 5
        }

        @Test
        @DisplayName("deve decrementar estoque apos reserva")
        void deveDecrementarEstoqueAposReserva() {
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));
            when(itemRepository.salvar(any(ItemEstoque.class))).thenReturn(teclado);
            when(reservaRepository.salvar(any(ReservaEstoque.class))).thenAnswer(inv -> inv.getArgument(0));

            estoqueService.reservar("pedido-001", "ABC-123", 10);

            assertThat(teclado.getQuantidade()).isEqualTo(32); // 42 - 10
            verify(itemRepository).salvar(teclado);
        }

        @Test
        @DisplayName("deve lancar excecao quando pedidoId e vazio")
        void deveLancarExcecaoQuandoPedidoIdEVazio() {
            assertThatThrownBy(() -> estoqueService.reservar("", "ABC-123", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pedidoId obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando sku e vazio")
        void deveLancarExcecaoQuandoSkuEVazio() {
            assertThatThrownBy(() -> estoqueService.reservar("pedido-001", "", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sku obrigatorio");
        }

        @Test
        @DisplayName("deve lancar excecao quando quantidade e menor ou igual a zero")
        void deveLancarExcecaoQuandoQuantidadeEMenorOuIgualAZero() {
            assertThatThrownBy(() -> estoqueService.reservar("pedido-001", "ABC-123", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("quantidade deve ser > 0");
        }

        @Test
        @DisplayName("deve lancar excecao quando SKU e desconhecido")
        void deveLancarExcecaoQuandoSkuEDesconhecido() {
            when(itemRepository.buscarPorSkuComLock("SKU-INVALIDO")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estoqueService.reservar("pedido-001", "SKU-INVALIDO", 1))
                    .isInstanceOf(SkuDesconhecidoException.class)
                    .hasMessageContaining("SKU desconhecido");
        }

        @Test
        @DisplayName("deve lancar excecao quando estoque e insuficiente")
        void deveLancarExcecaoQuandoEstoqueEInsuficiente() {
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));

            assertThatThrownBy(() -> estoqueService.reservar("pedido-001", "ABC-123", 100))
                    .isInstanceOf(EstoqueInsuficienteException.class)
                    .hasMessageContaining("Sem estoque para SKU");
        }
    }

    @Nested
    @DisplayName("cancelarReserva()")
    class CancelarReservaTests {

        @Test
        @DisplayName("deve cancelar reserva e restaurar estoque")
        void deveCancelarReservaERestaurarEstoque() {
            ReservaEstoque reserva = new ReservaEstoque("reserva-001", "ABC-123", 10, "pedido-001");
            when(reservaRepository.buscarPorId("reserva-001")).thenReturn(Optional.of(reserva));
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));
            when(itemRepository.salvar(any(ItemEstoque.class))).thenReturn(teclado);

            boolean ok = estoqueService.cancelarReserva("reserva-001");

            assertThat(ok).isTrue();
            assertThat(teclado.getQuantidade()).isEqualTo(52); // 42 + 10
            verify(reservaRepository).removerPorId("reserva-001");
        }

        @Test
        @DisplayName("deve retornar false ao cancelar reserva inexistente")
        void deveRetornarFalseAoCancelarReservaInexistente() {
            when(reservaRepository.buscarPorId("reserva-inexistente")).thenReturn(Optional.empty());

            boolean ok = estoqueService.cancelarReserva("reserva-inexistente");

            assertThat(ok).isFalse();
        }
    }

    @Nested
    @DisplayName("reservar() - Idempotencia")
    class IdempotenciaTests {

        @Test
        @DisplayName("deve retornar reserva existente quando pedidoId e sku ja foram reservados")
        void deveRetornarReservaExistenteQuandoPedidoIdESkuJaReservados() {
            ReservaEstoque reservaExistente = new ReservaEstoque("reserva-abc", "ABC-123", 5, "pedido-001");

            when(reservaRepository.existsByPedidoIdAndSku("pedido-001", "ABC-123")).thenReturn(true);
            when(reservaRepository.buscarPorPedidoIdESku("pedido-001", "ABC-123"))
                    .thenReturn(Optional.of(reservaExistente));

            ReservaEstoque reserva = estoqueService.reservar("pedido-001", "ABC-123", 5);

            assertThat(reserva).isNotNull();
            assertThat(reserva.getId()).isEqualTo("reserva-abc");
            assertThat(reserva.getSku()).isEqualTo("ABC-123");
            assertThat(reserva.getQuantidade()).isEqualTo(5);
            assertThat(reserva.getPedidoId()).isEqualTo("pedido-001");
            verify(itemRepository, never()).buscarPorSkuComLock(any());
            verify(itemRepository, never()).salvar(any());
            verify(reservaRepository, never()).salvar(any());
        }

        @Test
        @DisplayName("deve retornar reserva existente quando DataIntegrityViolationException occurs durante salvar")
        void deveRetornarReservaExistenteQuandoDataIntegrityViolationEmSalvar() {
            ReservaEstoque reservaExistente = new ReservaEstoque("reserva-concorrencia", "ABC-123", 5, "pedido-concorrencia");

            when(reservaRepository.existsByPedidoIdAndSku("pedido-concorrencia", "ABC-123")).thenReturn(false);
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));
            when(reservaRepository.salvar(any()))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));
            when(reservaRepository.buscarPorPedidoIdESku("pedido-concorrencia", "ABC-123"))
                    .thenReturn(Optional.of(reservaExistente));

            ReservaEstoque reserva = estoqueService.reservar("pedido-concorrencia", "ABC-123", 5);

            assertThat(reserva).isNotNull();
            assertThat(reserva.getId()).isEqualTo("reserva-concorrencia");
            verify(itemRepository).salvar(teclado);
        }

        @Test
        @DisplayName("deve criar nova reserva quando nao existe reserva para pedidoId e sku")
        void deveCriarNovaReservaQuandoNaoExisteReservaParaPedidoIdESku() {
            when(reservaRepository.existsByPedidoIdAndSku("pedido-novo", "ABC-123")).thenReturn(false);
            when(itemRepository.buscarPorSkuComLock("ABC-123")).thenReturn(Optional.of(teclado));
            when(reservaRepository.salvar(any(ReservaEstoque.class))).thenAnswer(inv -> inv.getArgument(0));

            ReservaEstoque reserva = estoqueService.reservar("pedido-novo", "ABC-123", 3);

            assertThat(reserva).isNotNull();
            assertThat(reserva.getId()).isNotBlank();
            assertThat(reserva.getSku()).isEqualTo("ABC-123");
            assertThat(reserva.getQuantidade()).isEqualTo(3);
            assertThat(reserva.getPedidoId()).isEqualTo("pedido-novo");
            assertThat(teclado.getQuantidade()).isEqualTo(39); // 42 - 3
            verify(reservaRepository).salvar(any(ReservaEstoque.class));
        }

        @Test
        @DisplayName("deve decrementar estoque apenas uma vez quando reserva ja existe")
        void deveDecrementarEstoqueApenasUmaVezQuandoReservaJaExiste() {
            ReservaEstoque reservaExistente = new ReservaEstoque("reserva-abc", "ABC-123", 5, "pedido-001");

            when(reservaRepository.existsByPedidoIdAndSku("pedido-001", "ABC-123")).thenReturn(true);
            when(reservaRepository.buscarPorPedidoIdESku("pedido-001", "ABC-123"))
                    .thenReturn(Optional.of(reservaExistente));

            estoqueService.reservar("pedido-001", "ABC-123", 5);

            assertThat(teclado.getQuantidade()).isEqualTo(42); // nao decrementado
            verify(itemRepository, never()).salvar(any());
        }
    }
}
