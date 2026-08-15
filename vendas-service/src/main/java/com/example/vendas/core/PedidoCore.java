package com.example.vendas.core;

import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import com.example.vendas.integration.IntegracoesService;
import com.example.vendas.integration.dto.FreteResponse;
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

        // === Etapa 1: Reserva de Estoque ===
        log.info("Solicitando reserva de estoque (pedidoId={}, sku={}, quantidade={})", pedidoId, request.sku(), request.quantidade());
        ReservaResponse reserva = integracoes.reservarEstoque(pedidoId, request.sku(), request.quantidade());

        // Verifica se a reserva falhou (null) ou se o circuit breaker retornou status INDISPONIVEL.
        if (reserva == null || "INDISPONIVEL".equals(reserva.status())) {
            state.status = "FALHA_ESTOQUE";
            log.warn("Reserva de estoque nao concluida (pedidoId={}, status={}, causa={})",
                    pedidoId, state.status, reserva != null ? reserva.status() : "null");
            return state.toResponse();
        }
        state.reservaId = reserva.reservaId();
        state.status = "ESTOQUE_RESERVADO";
        log.info("Estoque reservado com sucesso (pedidoId={}, reservaId={}, status={})", pedidoId, state.reservaId, state.status);

        // === Etapa 2: Calculo de Frete ===
        log.info("Solicitando calculo de frete (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                pedidoId, request.sku(), request.quantidade(), request.cepDestino());
        FreteResponse frete = integracoes.calcularFrete(pedidoId, request.sku(), request.quantidade(), request.cepDestino());

        // Verifica se o frete falhou (null) ou se o circuit breaker retornou status INDISPONIVEL.
        if (frete == null || "INDISPONIVEL".equals(frete.status())) {
            state.status = "FALHA_FRETE";
            log.warn("Calculo de frete nao concluido (pedidoId={}, reservaId={}, status={}, causa={})",
                    pedidoId, state.reservaId, state.status, frete != null ? frete.status() : "null");
            // Best-effort: devolve o estoque caso o frete falhe.
            log.info("Iniciando compensacao best-effort de estoque (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            integracoes.cancelarReservaBestEffort(state.reservaId);
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            return state.toResponse();
        }
        state.freteId = frete.freteId();
        state.valorFrete = frete.valorFrete();
        state.prazoEntrega = frete.prazoEntrega();
        state.status = "FRETE_CALCULADO";
        log.info("Frete calculado com sucesso (pedidoId={}, freteId={}, valorFrete={}, prazoEntrega={}, status={})",
                pedidoId, state.freteId, state.valorFrete, state.prazoEntrega, state.status);

        // === Etapa 3: Processamento de Pagamento ===
        double valorTotal = request.valor() + state.valorFrete;
        log.info("Solicitando pagamento (pedidoId={}, valorProduto={}, valorFrete={}, valorTotal={})",
                pedidoId, request.valor(), state.valorFrete, valorTotal);
        PagamentoResponse pagamento = integracoes.processarPagamento(pedidoId, valorTotal);

        // Verifica se o pagamento falhou (null) ou se o circuit breaker retornou status FALHA_TRANSITORIA.
        if (pagamento == null || "FALHA_TRANSITORIA".equals(pagamento.status())) {
            state.status = "FALHA_PAGAMENTO";
            log.warn("Pagamento nao concluido (pedidoId={}, reservaId={}, freteId={}, status={}, causa={})",
                    pedidoId, state.reservaId, state.freteId, state.status, pagamento != null ? pagamento.status() : "null");
            // Best-effort: devolve estoque e cancela frete caso o pagamento falhe.
            log.info("Iniciando compensacao best-effort de estoque e frete (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, state.reservaId, state.freteId);
            integracoes.cancelarReservaBestEffort(state.reservaId);
            integracoes.cancelarFreteBestEffort(state.freteId);
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, state.reservaId, state.freteId);
            return state.toResponse();
        }

        // === Pedido Processado com Sucesso ===
        state.transacaoId = pagamento.transacaoId();
        state.status = "PAGO";
        log.info("Pagamento aprovado e pedido finalizado (pedidoId={}, reservaId={}, freteId={}, transacaoId={}, valorTotal={}, status={})",
                pedidoId, state.reservaId, state.freteId, state.transacaoId, valorTotal, state.status);
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
        if (request.cepDestino() == null || request.cepDestino().isBlank()) {
            throw new IllegalArgumentException("cepDestino obrigatorio");
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
        private volatile String freteId;
        private volatile double valorFrete;
        private volatile String prazoEntrega;
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
            return new PedidoResponse(pedidoId, status, sku, quantidade, valor, valorFrete, prazoEntrega,
                    reservaId, freteId, transacaoId, criadoEm.toString());
        }
    }
}
