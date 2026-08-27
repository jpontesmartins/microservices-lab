package com.example.vendas.core;

import com.example.vendas.core.dto.CriarPedidoRequest;
import com.example.vendas.core.dto.PedidoResponse;
import com.example.vendas.integration.BusinessException;
import com.example.vendas.integration.IntegracoesService;
import com.example.vendas.integration.PedidoCriadoProducer;
import com.example.vendas.integration.dto.FreteResponse;
import com.example.vendas.integration.dto.PagamentoResponse;
import com.example.vendas.integration.dto.PedidoCriadoEvent;
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
    private final PedidoCriadoProducer pedidoCriadoProducer;

    private final Map<String, PedidoState> pedidos = new ConcurrentHashMap<>();

    public PedidoCore(IntegracoesService integracoes, PedidoCriadoProducer pedidoCriadoProducer) {
        this.integracoes = integracoes;
        this.pedidoCriadoProducer = pedidoCriadoProducer;
    }

    /**
     * Cria e processa um novo pedido seguindo o padrão saga com 3 etapas:
     * reserva de estoque, cálculo de frete e processamento de pagamento.
     * Em caso de falha, executa transações compensatórias (best-effort).
     *
     * @param request dados do pedido a ser criado
     * @return resposta do pedido com status e identificadores das integracoes
     */
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
        ReservaResponse reserva;
        try {
            reserva = integracoes.reservarEstoque(pedidoId, request.sku(), request.quantidade());
        } catch (BusinessException ex) {
            state.status = ex.getStatus();
            log.warn("Reserva de estoque falhou por erro de negocio (pedidoId={}, status={}, causa={})",
                    pedidoId, state.status, ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            return state.toResponse();
        }

        if (reserva == null || "FALHA_TRANSITORIA".equals(reserva.status())) {
            state.status = "FALHA_TRANSITORIA";
            log.warn("Reserva de estoque nao concluida (pedidoId={}, causa=null)", pedidoId);
            return state.toResponse();
        }
        state.reservaId = reserva.reservaId();
        state.status = "ESTOQUE_RESERVADO";
        log.info("Estoque reservado com sucesso (pedidoId={}, reservaId={}, status={})", pedidoId, state.reservaId, state.status);

        // === Etapa 2: Calculo de Frete ===
        log.info("Solicitando calculo de frete (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                pedidoId, request.sku(), request.quantidade(), request.cepDestino());
        FreteResponse frete;
        try {
            frete = integracoes.calcularFrete(pedidoId, request.sku(), request.quantidade(), request.cepDestino());
        } catch (BusinessException ex) {
            state.status = ex.getStatus();
            log.warn("Calculo de frete falhou por erro de negocio (pedidoId={}, reservaId={}, status={}, causa={})",
                    pedidoId, state.reservaId, state.status, ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            log.info("Iniciando compensacao best-effort de estoque (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            integracoes.cancelarReservaBestEffort(state.reservaId);
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={})", pedidoId, state.reservaId);
            return state.toResponse();
        }

        if (frete == null || "FALHA_TRANSITORIA".equals(frete.status())) {
            state.status = "FALHA_TRANSITORIA";
            log.warn("Calculo de frete nao concluido (pedidoId={}, reservaId={}, causa=null)", pedidoId, state.reservaId);
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
        PagamentoResponse pagamento;
        try {
            pagamento = integracoes.processarPagamento(pedidoId, valorTotal);
        } catch (BusinessException ex) {
            state.status = ex.getStatus();
            log.warn("Pagamento falhou por erro de negocio (pedidoId={}, reservaId={}, freteId={}, status={}, causa={})",
                    pedidoId, state.reservaId, state.freteId, state.status,
                    ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            log.info("Iniciando compensacao best-effort de estoque e frete (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, state.reservaId, state.freteId);
            integracoes.cancelarReservaBestEffort(state.reservaId);
            integracoes.cancelarFreteBestEffort(state.freteId);
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, state.reservaId, state.freteId);
            return state.toResponse();
        }

        if (pagamento == null || "FALHA_TRANSITORIA".equals(pagamento.status())) {
            state.status = "FALHA_TRANSITORIA";
            log.warn("Pagamento nao concluido (pedidoId={}, reservaId={}, freteId={}, causa=null)",
                    pedidoId, state.reservaId, state.freteId);
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

        // === Publicar evento PedidoCriado no Kafka ===
        PedidoCriadoEvent event = new PedidoCriadoEvent(pedidoId, request.sku(), request.quantidade(), valorTotal, request.cepDestino());
        pedidoCriadoProducer.publish(event);

        return state.toResponse();
    }

    /**
     * Busca um pedido pelo identificador no repositorio em memoria.
     *
     * @param pedidoId identificador do pedido
     * @return resposta do pedido ou null se nao encontrado
     */
    public PedidoResponse buscar(String pedidoId) {
        log.info("Buscando pedido no repositório em memoria (pedidoId={})", pedidoId);
        PedidoState state = pedidos.get(pedidoId);
        if (state == null) {
            log.warn("Pedido nao encontrado no repositório em memoria (pedidoId={})", pedidoId);
        }
        return state == null ? null : state.toResponse();
    }

    /**
     * Valida os dados obrigatórios do request de criação de pedido.
     *
     * @param request dados do pedido a serem validados
     * @throws IllegalArgumentException se algum campo obrigatório estiver inválido
     */
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
