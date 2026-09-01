package com.example.estoque.infrastructure;

import com.example.estoque.application.EstoqueService;
import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import com.example.estoque.shared.exception.EstoqueInsuficienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EstoqueConcurrencyTest {

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private ItemEstoqueRepositoryPort itemRepository;

    @Autowired
    private com.example.estoque.infrastructure.repository.ItemEstoqueJpaRepository itemJpaRepository;

    @BeforeEach
    void setUp() {
        itemJpaRepository.deleteAll();
        itemRepository.salvar(new ItemEstoque("ABC-123", "Teclado Mecanico", 5));
    }

    @Test
    @DisplayName("concorrentes devem respeitar estoque disponivel (race condition)")
    void deveImpedirRaceConditionNaReserva() throws Exception {
        int threads = 10;
        int quantidadePorThread = 3;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    estoqueService.reservar("pedido-" + idx, "ABC-123", quantidadePorThread);
                    return "SUCESSO";
                } catch (EstoqueInsuficienteException e) {
                    return "FALHA";
                } catch (Exception e) {
                    return "ERRO: " + e.getClass().getSimpleName();
                }
            }));
        }

        latch.countDown();

        long sucessos = 0;
        long falhas = 0;
        for (Future<String> f : futures) {
            String resultado = f.get();
            if ("SUCESSO".equals(resultado)) sucessos++;
            else falhas++;
        }

        executor.shutdown();

        System.out.println("=== RESULTADO CONCORRENCIA ===");
        System.out.println("Sucessos: " + sucessos + " / " + threads);
        System.out.println("Falhas:   " + falhas + " / " + threads);

        ItemEstoque estoqueFinal = itemRepository.buscarPorSku("ABC-123").orElseThrow();
        System.out.println("Estoque inicial: 5, reservado: " + (sucessos * quantidadePorThread) + ", estoque final: " + estoqueFinal.getQuantidade());

        assertThat(sucessos)
                .as("Apenas 1 thread deveria conseguir reservar (5 >= 3)")
                .isEqualTo(1);

        assertThat(estoqueFinal.getQuantidade())
                .as("Estoque final deveria ser 5 - 3 = 2")
                .isEqualTo(2);
    }
}
