package com.example.vendas.pedido.infrastructure;

import com.example.vendas.pedido.application.PedidoService;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Garantia de @Transactional na camada de servico")
class OutboxTransactionalAnnotationTest {

    @Test
    @DisplayName("criarPedido deve possuir anotacao @Transactional")
    void criarPedidoDevePossuirAnotacaoTransacional() throws NoSuchMethodException {
        var method = PedidoService.class.getMethod("criarPedido",
                CriarPedidoRequest.class, String.class);

        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("@Transactional deve estar presente em criarPedido()")
                .isTrue();
    }

    @Test
    @DisplayName("buscar deve possuir anotacao @Transactional(readOnly = true)")
    void buscarDevePossuirAnotacaoTransacionalReadOnly() throws NoSuchMethodException {
        var method = PedidoService.class.getMethod("buscar", String.class);

        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

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
}
