package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Garantia de @Transactional na camada de servico para compensacoes")
class CompensacaoTransactionalAnnotationTest {

    @Test
    @DisplayName("compensarEstoque nao deve possuir @Transactional propria (participa da tx do pai)")
    void compensarEstoqueNaoDevePossuirPropriaTransacional() throws NoSuchMethodException {
        var method = PedidoService.class.getDeclaredMethod("compensarEstoque",
                com.example.vendas.pedido.domain.model.Pedido.class);
        method.setAccessible(true);

        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("compensarEstoque nao deve ter @Transactional proprio")
                .isFalse();
    }

    @Test
    @DisplayName("compensarEstoqueEFrete nao deve possuir @Transactional propria (participa da tx do pai)")
    void compensarEstoqueEFreteNaoDevePossuirPropriaTransacional() throws NoSuchMethodException {
        var method = PedidoService.class.getDeclaredMethod("compensarEstoqueEFrete",
                com.example.vendas.pedido.domain.model.Pedido.class);
        method.setAccessible(true);

        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("compensarEstoqueEFrete nao deve ter @Transactional proprio")
                .isFalse();
    }

    @Test
    @DisplayName("criarPedido deve ter @Transactional")
    void criarPedidoDeveTerTransacional() throws NoSuchMethodException {
        var method = PedidoService.class.getMethod("criarPedido",
                com.example.vendas.pedido.web.dto.CriarPedidoRequest.class, String.class);

        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("criarPedido deve ter @Transactional")
                .isTrue();
    }
}
