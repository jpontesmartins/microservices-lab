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
import com.example.vendas.shared.exception.TransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]+$");
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

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
     * 1. reserva de estoque (por item), 2. calculo de frete (por item) e 3. processamento de pagamento (total).
     * Ao finalizar, publica na fila o pedido criado
     * Em caso de falha, executa transacoes compensatorias (best-effort).
     *
     * Suporta idempotencia via Idempotency-Key: se fornecido, usa como pedidoId;
     * se ja existe pedido com esse ID, retorna o existente sem reprocessar.
     *
     * @param request dados do pedido a ser criado
     * @param idempotencyKey chave de idempotencia (opcional, header Idempotency-Key)
     * @return resposta do pedido com status e identificadores das integracoes
     */
    public PedidoResponse criarPedido(CriarPedidoRequest request, String idempotencyKey) {
        validar(request);
        validarIdempotencyKey(idempotencyKey);

        String pedidoId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        if (pedidoRepository.existsById(pedidoId)) {
            log.info("Pedido ja existe, retornando existente (pedidoId={})", pedidoId);
            return toResponse(pedidoRepository.buscarPorId(pedidoId).orElseThrow());
        }

        log.info("Iniciando fluxo de pedido (pedidoId={}, cepDestino={}, totalItens={})",
                pedidoId, request.cepDestino(), request.items().size());

        Pedido pedido = Pedido.criar(pedidoId, request.cepDestino());
        for (ItemPedidoRequest itemReq : request.items()) {
            pedido.adicionarItem(ItemPedido.criar(itemReq.sku(), itemReq.quantidade(), itemReq.valor()));
        }
        try {
            pedidoRepository.salvar(pedido);
        } catch (DataIntegrityViolationException e) {
            log.warn("Pedido duplicado detectado via concorrencia, retornando existente (pedidoId={})", pedidoId);
            return toResponse(pedidoRepository.buscarPorId(pedidoId).orElseThrow());
        }

        // === Etapa 1: Reserva de Estoque (por item) ===
        for (ItemPedido item : pedido.getItems()) {
            log.info("Solicitando reserva de estoque (pedidoId={}, sku={}, quantidade={})",
                    pedidoId, item.getSku(), item.getQuantidade());
            ReservaEstoqueResult reserva;
            try {
                reserva = integracoes.reservarEstoque(pedidoId, item.getSku(), item.getQuantidade());
            } catch (BusinessException ex) {
                pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()), ex.getUserMessage());
                pedidoRepository.salvar(pedido);
                log.warn("Reserva de estoque falhou por erro de negocio (pedidoId={}, sku={}, status={})",
                        pedidoId, item.getSku(), ex.getStatus());
                throw ex;
            }

            if (reserva == null || "FALHA_TRANSITORIA".equals(reserva.status())) {
                String mensagem = "Servico de estoque temporariamente indisponivel";
                pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA, mensagem);
                pedidoRepository.salvar(pedido);
                log.warn("Reserva de estoque nao concluida (pedidoId={}, sku={})", pedidoId, item.getSku());
                throw new TransientException(mensagem, null);
            }
            item.reservarEstoque(reserva.reservaId());
            log.info("Estoque reservado com sucesso (pedidoId={}, sku={}, reservaId={})", pedidoId, item.getSku(),
                    item.getReservaId());
        }
        pedido.reservarEstoque("");
        pedidoRepository.salvar(pedido);
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
                pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()), ex.getUserMessage());
                compensarEstoque(pedido);
                pedidoRepository.salvar(pedido);
                log.warn("Calculo de frete falhou por erro de negocio (pedidoId={}, sku={}, status={})",
                        pedidoId, item.getSku(), ex.getStatus());
                throw ex;
            }

            if (frete == null || "FALHA_TRANSITORIA".equals(frete.status())) {
                String mensagem = "Servico de frete temporariamente indisponivel";
                pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA, mensagem);
                compensarEstoque(pedido);
                pedidoRepository.salvar(pedido);
                log.warn("Calculo de frete nao concluido (pedidoId={}, sku={})", pedidoId, item.getSku());
                throw new TransientException(mensagem, null);
            }
            item.calcularFrete(frete.freteId(), frete.valorFrete(), frete.prazoEntrega());
            log.info("Frete calculado com sucesso (pedidoId={}, sku={}, freteId={}, valorFrete={}, prazoEntrega={})",
                    pedidoId, item.getSku(), item.getFreteId(), item.getValorFrete(), item.getPrazoEntrega());
        }
        pedido.calcularFrete();
        pedidoRepository.salvar(pedido);
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
            pedido.marcarFalha(StatusPedido.valueOf(ex.getStatus()), ex.getUserMessage());
            compensarEstoqueEFrete(pedido);
            pedidoRepository.salvar(pedido);
            log.warn("Pagamento falhou por erro de negocio (pedidoId={}, status={})",
                    pedidoId, ex.getStatus());
            throw ex;
        }

        if (pagamento == null || "FALHA_TRANSITORIA".equals(pagamento.status())) {
            String mensagem = "Servico de pagamento temporariamente indisponivel";
            pedido.marcarFalha(StatusPedido.FALHA_TRANSITORIA, mensagem);
            compensarEstoqueEFrete(pedido);
            pedidoRepository.salvar(pedido);
            log.warn("Pagamento nao concluido (pedidoId={})", pedidoId);
            throw new TransientException(mensagem, null);
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
                .map(p -> toResponse(p))
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
                pedido.getCriadoEm().toString(),
                pedido.getMensagemErro());
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

    private static void validarIdempotencyKey(String key) {
        if (key != null) {
            if (key.isBlank()) {
                throw new IllegalArgumentException("Idempotency-Key nao pode ser vazio");
            }
            if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Idempotency-Key maximo " + IDEMPOTENCY_KEY_MAX_LENGTH + " caracteres");
            }
            if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "Idempotency-Key deve conter apenas alfanumerico e hifens");
            }
        }
    }
}
