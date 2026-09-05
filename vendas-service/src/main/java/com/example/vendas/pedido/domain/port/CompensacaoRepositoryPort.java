package com.example.vendas.pedido.domain.port;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;

import java.util.List;

/**
 * Porta de saida para persistencia de compensacoes pendentes do saga.
 * Garante que cancelamentos de reserva/frete sejam registrados na mesma transacao
 * do update do pedido, permitindo retry posterior via polling.
 */
public interface CompensacaoRepositoryPort {

        void salvarCompensacao(String pedidoId, TipoCompensacao tipo, String refId, int maxAttempts);

    List<CompensacaoPendente> buscarPendentesParaProcessar();

    void marcarEnviado(List<Long> ids);

    void marcarFalha(List<Long> ids);

    long contarPendentes();

    record CompensacaoPendente(
            Long id,
            String pedidoId,
            TipoCompensacao tipo,
            String refId,
            StatusCompensacao status,
            int attempts,
            int maxAttempts,
            String lastError
    ) {}
}
