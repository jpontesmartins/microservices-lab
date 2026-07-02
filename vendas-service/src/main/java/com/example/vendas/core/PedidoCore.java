package com.example.vendas.core;

import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import com.example.vendas.integration.IntegracoesService;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.ReservaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PedidoCore {

    private static final Logger log = LoggerFactory.getLogger(PedidoCore.class);

    private final IntegracoesService integracoes;

    private final Map<String, PedidoState> pedidos = new ConcurrentHashMap<>();

    public PedidoCore(IntegracoesService integracoes) {
        this.integracoes = integracoes;
    }

    public PedidoResponse criarPedido(CriarPedidoRequest request) {
        validar(request);

        String pedidoId = UUID.randomUUID().toString();
        log.info("Iniciando fluxo de pedido (pedidoId={}, sku={}, quantidade={}, valor={})",
                pedidoId, request.sku(), request.quantidade(), request.valor());
        PedidoState state = new PedidoState(pedidoId, request.sku(), request.quantidade(), request.valor(), "CRIADO", Instant.now());
        pedidos.put(pedidoId, state);
        log.info("Pedido persistido em memoria (pedidoId={}, status={})", pedidoId, state.status);

        log.info("Solicitando reserva de estoque (pedidoId={}, sku={}, quantidade={})", pedidoId, request.sku(), request.quantidade());
        ReservaResponse reserva = integracoes.reservarEstoque(pedidoId, request.sku(), request.quantidade());
        if (reserva == null) {
            state.status = "FALHA_ESTOQUE";
            log.warn("Reserva de estoque nao concluida (pedidoId={}, status={})", pedidoId, state.status);
            return state.toResponse();
        }
        state.reservaId = reserva.reservaId();
        state.status = "ESTOQUE_RESERVADO";
        log.info("Estoque reservado com sucesso (pedidoId={}, reservaId={}, status={})", pedidoId, state.reservaId, state.status);

        log.info("Solicitando pagamento (pedidoId={}, valor={})", pedidoId, request.valor());
        PagamentoResponse pagamento = integracoes.processarPagamento(pedidoId, request.valor());
        if (pagamento == null) {
            state.status = "FALHA_PAGAMENTO";
            log.warn("Pagamento nao concluido (pedidoId={}, reservaId={}, status={})", pedidoId, state.reservaId, state.status);
            // Best-effort: devolve o estoque caso o pagamento falhe.
            log.info("Iniciando compensacao best-effort de estoque (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            integracoes.cancelarReservaBestEffort(state.reservaId);
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            return state.toResponse();
        }

        state.transacaoId = pagamento.transacaoId();
        state.status = "PAGO";
        log.info("Pagamento aprovado e pedido finalizado (pedidoId={}, reservaId={}, transacaoId={}, status={})",
                pedidoId, state.reservaId, state.transacaoId, state.status);
        return state.toResponse();
    }

    public PedidoResponse buscar(String pedidoId) {
        log.info("Buscando pedido no repositório em memoria (pedidoId={})", pedidoId);
        PedidoState state = pedidos.get(pedidoId);
        if (state == null) {
            log.warn("Pedido nao encontrado no repositório em memoria (pedidoId={})", pedidoId);
        }
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
