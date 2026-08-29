package com.example.vendas.pedido.application;

import com.example.vendas.pedido.domain.model.ItemPedido;
import com.example.vendas.pedido.domain.model.Pedido;
import com.example.vendas.pedido.domain.model.StatusPedido;
import com.example.vendas.pedido.domain.port.EventoPublicacaoPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort;
import com.example.vendas.pedido.domain.port.IntegracoesPort.FreteResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.PagamentoResult;
import com.example.vendas.pedido.domain.port.IntegracoesPort.ReservaEstoqueResult;
import com.example.vendas.pedido.domain.port.PedidoRepositoryPort;
import com.example.vendas.pedido.web.dto.CriarPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoRequest;
import com.example.vendas.pedido.web.dto.ItemPedidoResponse;
import com.example.vendas.pedido.web.dto.PedidoResponse;
import com.example.vendas.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
     * reserva de estoque (por item), calculo de frete (por item) e processamento de pagamento (total).
     * Em caso de falha, executa transacoes compensatorias (best-effort).
     *
     * @param request dados do pedido a ser criado
     * @return resposta do pedido com status e identificadores das integracoes
     */
    public PedidoResponse criarPedido(CriarPedidoRequest request) {
        validar(request);

        String pedidoId = UUID.randomUUID().toString();
        log.info("Iniciando fluxo de pedido (pedidoId={}, cepDestino={}, totalItens={})",
                pedidoId, request.cepDestino(), request.items().size());

        Pedido pedido = Pedido.criar(pedidoId, request.cepDestino());
        for (ItemPedidoRequest itemReq : request.items()) {
            pedido.adicionarItem(ItemPedido.criar(itemReq.sku(), itemReq.quantidade(), itemReq.valor()));
        }

        // === Etapa 1: Reserva de Estoque (por item) ===
        for (ItemPedido item : pedido.getItems()) {
            log.info("Solicitando reserva de estoque (pedidoId={}, sku={}, quantidade={})",
                    pedidoId, item.getSku(), item.getQuantidade());
            ReservaEstoqueResult reserva;
            try {
                reserva = integracoes.reservarEstoque(pedidoId, item.getSku(), item.getQuantidade());
            } catch (BusinessException ex) {
                pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
                log.warn("Reserva de estoque falhou por erro de negocio (pedidoId={}, sku={}, status={}, causa={})",
                        pedidoId, item.getSku(), pedido.getStatus(),
                        ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
                return toResponse(pedido);
            }

            if (reserva == null || "FALHA_TRANSITORIA".equals(reserva.status())) {
                pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
                log.warn("Reserva de estoque nao concluida (pedidoId={}, sku={}, causa=null)", pedidoId, item.getSku());
                return toResponse(pedido);
            }
            item.reservarEstoque(reserva.reservaId());
            log.info("Estoque reservado com sucesso (pedidoId={}, sku={}, reservaId={})", pedidoId, item.getSku(),
                    item.getReservaId());
        }
        pedido.reservarEstoque("");
        log.info("Todos os itens reservados com sucesso (pedidoId={}, status={})", pedidoId, pedido.getStatus());

        // === Etapa 2: Calculo de Frete (por item) ===
        for (ItemPedido item : pedido.getItems()) {
            log.info("Solicitando calculo de frete (pedidoId={}, sku={}, quantidade={}, cepDestino={})",
                    pedidoId, item.getSku(), item.getQuantidade(), request.cepDestino());
            FreteResult frete;
            try {
                frete = integracoes.calcularFrete(pedidoId, item.getSku(), item.getQuantidade(),
                        request.cepDestino());
            } catch (BusinessException ex) {
                pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
                log.warn("Calculo de frete falhou por erro de negocio (pedidoId={}, sku={}, status={}, causa={})",
                        pedidoId, item.getSku(), pedido.getStatus(),
                        ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
                compensarEstoque(pedido);
                return toResponse(pedido);
            }

            if (frete == null || "FALHA_TRANSITORIA".equals(frete.status())) {
                pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
                log.warn("Calculo de frete nao concluido (pedidoId={}, sku={}, causa=null)", pedidoId, item.getSku());
                compensarEstoque(pedido);
                return toResponse(pedido);
            }
            item.calcularFrete(frete.freteId(), frete.valorFrete(), frete.prazoEntrega());
            log.info("Frete calculado com sucesso (pedidoId={}, sku={}, freteId={}, valorFrete={}, prazoEntrega={})",
                    pedidoId, item.getSku(), item.getFreteId(), item.getValorFrete(), item.getPrazoEntrega());
        }
        pedido.calcularFrete();
        log.info("Frete calculado para todos os itens (pedidoId={}, status={})", pedidoId, pedido.getStatus());

        // === Etapa 3: Processamento de Pagamento ===
        double valorTotal = pedido.calcularValorTotal();
        double valorFreteTotal = pedido.calcularValorFreteTotal();
        log.info("Solicitando pagamento (pedidoId={}, valorFreteTotal={}, valorTotal={})",
                pedidoId, valorFreteTotal, valorTotal);
        PagamentoResult pagamento;
        try {
            pagamento = integracoes.processarPagamento(pedidoId, valorTotal);
        } catch (BusinessException ex) {
            pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()));
            log.warn("Pagamento falhou por erro de negocio (pedidoId={}, status={}, causa={})",
                    pedidoId, pedido.getStatus(),
                    ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "desconhecida");
            compensarEstoqueEFrete(pedido);
            return toResponse(pedido);
        }

        if (pagamento == null || "FALHA_TRANSITORIA".equals(pagamento.status())) {
            pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA);
            log.warn("Pagamento nao concluido (pedidoId={}, causa=null)", pedidoId);
            compensarEstoqueEFrete(pedido);
            return toResponse(pedido);
        }

        // === Pedido Processado com Sucesso ===
        pedido.confirmarPagamento(pagamento.transacaoId());
        log.info("Pagamento aprovado e pedido finalizado (pedidoId={}, transacaoId={}, valorTotal={}, status={})",
                pedidoId, pedido.getTransacaoId(), valorTotal, pedido.getStatus());

        pedidoRepository.salvar(pedido);
        eventoPublicacao.publicarPedidoCriado(pedido);

        return toResponse(pedido);
    }

    /**
     * Busca um pedido pelo identificador no repositorio.
     *
     * @param pedidoId identificador do pedido
     * @return resposta do pedido ou null se nao encontrado
     */
    public PedidoResponse buscar(String pedidoId) {
        log.info("Buscando pedido no repositorio (pedidoId={})", pedidoId);
        return pedidoRepository.buscarPorId(pedidoId)
                .map(PedidoService::toResponse)
                .orElse(null);
    }

    private void compensarEstoque(Pedido pedido) {
        log.info("Iniciando compensacao best-effort de estoque (pedidoId={})", pedido.getPedidoId());
        for (ItemPedido item : pedido.getItems()) {
            if (item.getReservaId() != null) {
                integracoes.cancelarReservaBestEffort(item.getReservaId());
                log.info("Compensacao de estoque realizada (pedidoId={}, sku={}, reservaId={})",
                        pedido.getPedidoId(), item.getSku(), item.getReservaId());
            }
        }
        log.info("Compensacao best-effort de estoque concluida (pedidoId={})", pedido.getPedidoId());
    }

    private void compensarEstoqueEFrete(Pedido pedido) {
        log.info("Iniciando compensacao best-effort de estoque e frete (pedidoId={})", pedido.getPedidoId());
        for (ItemPedido item : pedido.getItems()) {
            if (item.getReservaId() != null) {
                integracoes.cancelarReservaBestEffort(item.getReservaId());
                log.info("Compensacao de estoque realizada (pedidoId={}, sku={}, reservaId={})",
                        pedido.getPedidoId(), item.getSku(), item.getReservaId());
            }
            if (item.getFreteId() != null) {
                integracoes.cancelarFreteBestEffort(item.getFreteId());
                log.info("Compensacao de frete realizada (pedidoId={}, sku={}, freteId={})",
                        pedido.getPedidoId(), item.getSku(), item.getFreteId());
            }
        }
        log.info("Compensacao best-effort de estoque e frete concluida (pedidoId={})", pedido.getPedidoId());
    }

    static PedidoResponse toResponse(Pedido pedido) {
        List<ItemPedidoResponse> itemResponses = new ArrayList<>();
        for (ItemPedido item : pedido.getItems()) {
            itemResponses.add(new ItemPedidoResponse(
                    item.getSku(),
                    item.getQuantidade(),
                    item.getValorUnitario(),
                    item.getSubtotal(),
                    item.getValorFrete(),
                    item.getPrazoEntrega(),
                    item.getReservaId(),
                    item.getFreteId()));
        }

        return new PedidoResponse(
                pedido.getPedidoId(),
                pedido.getStatus().name(),
                itemResponses,
                pedido.calcularValorTotal(),
                pedido.calcularValorFreteTotal(),
                pedido.getTransacaoId(),
                pedido.getCriadoEm().toString());
    }

    private static void validar(CriarPedidoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Body obrigatorio");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("items obrigatorio e nao pode ser vazio");
        }
        if (request.cepDestino() == null || request.cepDestino().isBlank()) {
            throw new IllegalArgumentException("cepDestino obrigatorio");
        }
        for (int i = 0; i < request.items().size(); i++) {
            ItemPedidoRequest item = request.items().get(i);
            if (item == null) {
                throw new IllegalArgumentException("item[" + i + "] nao pode ser null");
            }
            if (item.sku() == null || item.sku().isBlank()) {
                throw new IllegalArgumentException("item[" + i + "].sku obrigatorio");
            }
            if (item.quantidade() <= 0) {
                throw new IllegalArgumentException("item[" + i + "].quantidade deve ser > 0");
            }
            if (item.valor() <= 0) {
                throw new IllegalArgumentException("item[" + i + "].valor deve ser > 0");
            }
        }
    }
}
