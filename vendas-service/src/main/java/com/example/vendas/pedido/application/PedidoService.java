package com.example.vendas.pedido.application;

import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.model.StatusPedido;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final IntegracoesPort integracoes;
    private final PedidoRepositoryPort pedidoRepository;
    private final EventoPublicacaoPort eventoPublicacao;

    public PedidoService(IntegracoesPort integracoes, PedidoRepositoryPort pedidoRepository,
            EventoPublicacaoPort eventoPublicacao) {
        this.integracoes = integracoes;
        this.pedidoRepository = pedidoRepository;
        this.eventoPublicacao = eventoPublicacao;
    }

    /**
     * Cria e processa um novo pedido seguindo o padrao saga com 3 etapas:
     * reserva de estoque, calculo de frete e processamento de pagamento.
     * Em caso de falha, executa transacoes compensatorias (best-effort).
     *
     * @param request dados do pedido a ser criado
     * @return resposta do pedido com status e identificadores das integracoes
     */
    public PedidoResponse criarPedido(CriarPedidoRequest request) {
        validar(request);

        String pedidoId = UUID.randomUUID().toString();
        log.info("Iniciando fluxo de pedido (pedidoId={}, sku={}, quantidade={}, valor={})",
                pedidoId, request.sku(), request.quantidade(), request.valor());

        Pedido pedido = Pedido.criar(pedidoId, request.sku(), request.quantidade(), request.valor(),
                request.cepDestino());

        // === Etapa 1: Reserva de Estoque ===
        log.info("Solicitando reserva de estoque (pedidoId={}, sku={}, quantidade={})", pedidoId, request.sku(),
                request.quantidade());
        ReservaEstoqueResult reserva;
        try {
            reserva = integracoes.reservarEstoque(pedidoId, request.sku(), request.quantidade());
        } catch (BusinessException ex) {
            pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
            log.warn("Reserva de estoque falhou por erro de negocio (pedidoId={}, status={}, causa={})",
                    pedidoId, pedido.getStatus(), ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            return toResponse(pedido);
        }

        if (reserva == null || "FALHA_TRANSITORIA".equals(reserva.status())) {
            pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
            log.warn("Reserva de estoque nao concluida (pedidoId={}, causa=null)", pedidoId);
            return toResponse(pedido);
        }
        pedido.reservarEstoque(reserva.reservaId());
        log.info("Estoque reservado com sucesso (pedidoId={}, reservaId={}, status={})", pedidoId, pedido.getReservaId(),
                pedido.getStatus());

        // === Etapa 2: Calculo de Frete ===
        log.info("Solicitando calculo de frete (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                pedidoId, request.sku(), request.quantidade(), request.cepDestino());
        FreteResult frete;
        try {
            frete = integracoes.calcularFrete(pedidoId, request.sku(), request.quantidade(), request.cepDestino());
        } catch (BusinessException ex) {
            pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
            log.warn("Calculo de frete falhou por erro de negocio (pedidoId={}, reservaId={}, status={}, causa={})",
                    pedidoId, pedido.getReservaId(), pedido.getStatus(),
                    ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            log.info("Iniciando compensacao best-effort de estoque (pedidoId={}, reservaId={})", pedidoId,
                    pedido.getReservaId());
            integracoes.cancelarReservaBestEffort(pedido.getReservaId());
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={})", pedidoId,
                    pedido.getReservaId());
            return toResponse(pedido);
        }

        if (frete == null || "FALHA_TRANSITORIA".equals(frete.status())) {
            pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
            log.warn("Calculo de frete nao concluido (pedidoId={}, reservaId={}, causa=null)", pedidoId,
                    pedido.getReservaId());
            log.info("Iniciando compensacao best-effort de estoque (pedidoId={}, reservaId={})", pedidoId,
                    pedido.getReservaId());
            integracoes.cancelarReservaBestEffort(pedido.getReservaId());
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={})", pedidoId,
                    pedido.getReservaId());
            return toResponse(pedido);
        }
        pedido.calcularFrete(frete.freteId(), frete.valorFrete(), frete.prazoEntrega());
        log.info("Frete calculado com sucesso (pedidoId={}, freteId={}, valorFrete={}, prazoEntrega={}, status={})",
                pedidoId, pedido.getFreteId(), pedido.getValorFrete(), pedido.getPrazoEntrega(), pedido.getStatus());

        // === Etapa 3: Processamento de Pagamento ===
        double valorTotal = pedido.calcularValorTotal();
        log.info("Solicitando pagamento (pedidoId={}, valorProduto={}, valorFrete={}, valorTotal={})",
                pedidoId, request.valor(), pedido.getValorFrete(), valorTotal);
        PagamentoResult pagamento;
        try {
            pagamento = integracoes.processarPagamento(pedidoId, valorTotal);
        } catch (BusinessException ex) {
            pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
            log.warn("Pagamento falhou por erro de negocio (pedidoId={}, reservaId={}, freteId={}, status={}, causa={})",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId(), pedido.getStatus(),
                    ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            log.info("Iniciando compensacao best-effort de estoque e frete (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId());
            integracoes.cancelarReservaBestEffort(pedido.getReservaId());
            integracoes.cancelarFreteBestEffort(pedido.getFreteId());
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId());
            return toResponse(pedido);
        }

        if (pagamento == null || "FALHA_TRANSITORIA".equals(pagamento.status())) {
            pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
            log.warn("Pagamento nao concluido (pedidoId={}, reservaId={}, freteId={}, causa=null)",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId());
            log.info("Iniciando compensacao best-effort de estoque e frete (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId());
            integracoes.cancelarReservaBestEffort(pedido.getReservaId());
            integracoes.cancelarFreteBestEffort(pedido.getFreteId());
            log.info("Compensacao best-effort concluida ou tentativa encerrada (pedidoId={}, reservaId={}, freteId={})",
                    pedidoId, pedido.getReservaId(), pedido.getFreteId());
            return toResponse(pedido);
        }

        // === Pedido Processado com Sucesso ===
        pedido.confirmarPagamento(pagamento.transacaoId());
        log.info("Pagamento aprovado e pedido finalizado (pedidoId={}, reservaId={}, freteId={}, transacaoId={}, valorTotal={}, status={})",
                pedidoId, pedido.getReservaId(), pedido.getFreteId(), pedido.getTransacaoId(), valorTotal,
                pedido.getStatus());

        pedidoRepository.salvar(pedido);
        eventoPublicacao.publicarPedidoCriado(pedido);

        return toResponse(pedido);
    }

    /**
     * Busca um pedido pelo identificador no repositorio em memoria.
     *
     * @param pedidoId identificador do pedido
     * @return resposta do pedido ou null se nao encontrado
     */
    public PedidoResponse buscar(String pedidoId) {
        log.info("Buscando pedido no repositorio em memoria (pedidoId={})", pedidoId);
        return pedidoRepository.buscarPorId(pedidoId)
                .map(PedidoService::toResponse)
                .orElse(null);
    }

    static PedidoResponse toResponse(Pedido pedido) {
        return new PedidoResponse(
                pedido.getPedidoId(),
                pedido.getStatus().name(),
                pedido.getSku(),
                pedido.getQuantidade(),
                pedido.getValor(),
                pedido.getValorFrete(),
                pedido.getPrazoEntrega(),
                pedido.getReservaId(),
                pedido.getFreteId(),
                pedido.getTransacaoId(),
                pedido.getCriadoEm().toString());
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
}
