package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort.CompensacaoPendente;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensacaoRepositoryAdapterTest {

    @Mock
    private CompensacaoPendenteJpaRepository jpaRepository;

    @InjectMocks
    private CompensacaoRepositoryAdapter adapter;

    @Test
    @DisplayName("deve salvar compensacao corretamente")
    void deveSalvarCompensacaoCorretamente() {
        adapter.salvarCompensacao("pedido-001", TipoCompensacao.ESTOQUE, "reserva-001", 3);

        verify(jpaRepository).save(any(CompensacaoPendenteEntity.class));
    }

    @Test
    @DisplayName("deve buscar pendentes para processar")
    void deveBuscarPendentesParaProcessar() {
        CompensacaoPendenteEntity entity = new CompensacaoPendenteEntity(
                "pedido-001", TipoCompensacao.ESTOQUE, "reserva-001", 3);

        when(jpaRepository.findPendentesParaProcessar(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(entity));

        List<CompensacaoPendente> result = adapter.buscarPendentesParaProcessar();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pedidoId()).isEqualTo("pedido-001");
        assertThat(result.get(0).tipo()).isEqualTo(TipoCompensacao.ESTOQUE);
        assertThat(result.get(0).refId()).isEqualTo("reserva-001");
    }

    @Test
    @DisplayName("deve contar pendentes")
    void deveContarPendentes() {
        CompensacaoPendenteEntity entity = new CompensacaoPendenteEntity(
                "pedido-001", TipoCompensacao.ESTOQUE, "reserva-001", 3);

        when(jpaRepository.findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE))
                .thenReturn(List.of(entity));

        long count = adapter.contarPendentes();

        assertThat(count).isEqualTo(1);
    }
}
