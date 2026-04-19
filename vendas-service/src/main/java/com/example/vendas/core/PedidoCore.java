package com.example.vendas.core;

import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import com.example.vendas.integration.IntegracoesService;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PedidoCore {

    private final IntegracoesService integracoes;

    private final Map<String, PedidoState> pedidos = new ConcurrentHashMap<>();

    public PedidoCore(IntegracoesService integracoes) {
        this.integracoes = integracoes;
    }

    public PedidoResponse criarPedido(CriarPedidoRequest request) {
        validar(request);

        String pedidoId = UUID.randomUUID().toString();
        PedidoState state = new PedidoState(pedidoId, request.sku(), request.quantidade(), request.valor(), "CRIADO", Instant.now());
        pedidos.put(pedidoId, state);

        ReservaResponse reserva = integracoes.reservarEstoque(pedidoId, request.sku(), request.quantidade());
        if (reserva == null) {
            state.status = "FALHA_ESTOQUE";
            return state.toResponse();
        }
        state.reservaId = reserva.reservaId();
        state.status = "ESTOQUE_RESERVADO";

        PagamentoResponse pagamento = integracoes.processarPagamento(pedidoId, request.valor());
        if (pagamento == null) {
            state.status = "FALHA_PAGAMENTO";
            // Best-effort: devolve o estoque caso o pagamento falhe.
            integracoes.cancelarReservaBestEffort(state.reservaId);
            return state.toResponse();
        }

        state.transacaoId = pagamento.transacaoId();
        state.status = "PAGO";
        return state.toResponse();
    }

    public PedidoResponse buscar(String pedidoId) {
        PedidoState state = pedidos.get(pedidoId);
        return state == null ? null : state.toResponse();
    }

    private static void validar(CriarPedidoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Body obrigatorio");
        }
        if (request.sku() == null || request.sku().isBlank()) {
            throw new IllegalArgumentException("sku obrigatorio");
        }
        if (request.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser > 0");
        }
        if (request.valor() <= 0) {
            throw new IllegalArgumentException("valor deve ser > 0");
        }
    }

    private static final class PedidoState {
        private final String pedidoId;
        private final String sku;
        private final int quantidade;
        private final double valor;
        private final Instant criadoEm;
        private volatile String status;
        private volatile String reservaId;
        private volatile String transacaoId;

        private PedidoState(String pedidoId, String sku, int quantidade, double valor, String status, Instant criadoEm) {
            this.pedidoId = pedidoId;
            this.sku = sku;
            this.quantidade = quantidade;
            this.valor = valor;
            this.status = status;
            this.criadoEm = criadoEm;
        }

        private PedidoResponse toResponse() {
            return new PedidoResponse(pedidoId, status, sku, quantidade, valor, reservaId, transacaoId, criadoEm.toString());
        }
    }
}
