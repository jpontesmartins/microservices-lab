package com.example.pagamento.domain.port;

import com.example.pagamento.domain.model.Transacao;

import java.util.Optional;

public interface TransacaoRepositoryPort {

    Optional<Transacao> buscarPorPedidoId(String pedidoId);

    void salvar(Transacao transacao);
}
