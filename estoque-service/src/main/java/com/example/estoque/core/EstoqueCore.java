package com.example.estoque.core;

import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Núcleo de lógica de negócio do serviço de estoque.
 * Gerencia itens em estoque e operações de reserva/cancelamento usando armazenamento em memória.
 */
@Service
public class EstoqueCore {

    private static final Logger log = LoggerFactory.getLogger(EstoqueCore.class);

    private final Map<String, Item> itens = new ConcurrentHashMap<>();
    private final Map<String, Reserva> reservas = new ConcurrentHashMap<>();

    /**
     * Construtor que inicializa o estoque com dados seed para o lab.
     */
    public EstoqueCore() {
        // Seed de estoque para o lab.
        itens.put("ABC-123", new Item("ABC-123", "Teclado Mecanico", 42));
        itens.put("XYZ-789", new Item("XYZ-789", "Mouse Gamer", 15));
    }

    /**
     * Lista todos os itens disponiveis em estoque.
     *
     * @return lista de itens com SKU, descricao e quantidade disponivel
     */
    public List<ItemEstoqueResponse> listarItens() {
        log.info("Montando lista de itens em estoque");
        List<ItemEstoqueResponse> out = new ArrayList<>();
        for (Item item : itens.values()) {
            out.add(new ItemEstoqueResponse(item.sku(), item.descricao(), item.quantidade()));
        }
        log.info("Lista de itens em estoque pronta (quantidade={})", out.size());
        return out;
    }

    /**
     * Cria uma reserva de estoque para um pedido.
     * Valida os dados, verifica disponibilidade e decrementa a quantidade atomicamente.
     *
     * @param request dados da reserva (pedidoId, sku, quantidade)
     * @return resposta da reserva criada com identificador unico
     * @throws IllegalArgumentException se request for invalido ou SKU desconhecido
     * @throws IllegalStateException se estoque insuficiente
     */
    public ReservaResponse reservar(ReservaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request nao pode ser null");
        }
        if (request.sku() == null || request.sku().isBlank()) {
            throw new IllegalArgumentException("sku obrigatorio");
        }
        if (request.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser > 0");
        }

        String sku = request.sku().trim();
        log.info("Processando reserva de estoque (pedidoId={}, sku={}, quantidade={})",
                request.pedidoId(), sku, request.quantidade());
        Item item = itens.get(sku);
        if (item == null) {
            log.warn("SKU desconhecido informado na reserva (pedidoId={}, sku={})", request.pedidoId(), sku);
            throw new IllegalArgumentException("SKU desconhecido: " + sku);
        }

        // Atualizacao atomica por SKU.
        synchronized (sku.intern()) {
            if (item.quantidade() < request.quantidade()) {
                log.warn("Estoque insuficiente para reserva (pedidoId={}, sku={}, disponivel={}, solicitado={})",
                        request.pedidoId(), sku, item.quantidade(), request.quantidade());
                throw new IllegalStateException("Sem estoque para SKU " + sku + " (disp=" + item.quantidade() + ")");
            }
            item.decrementar(request.quantidade());
            log.info("Quantidade reservada com sucesso (pedidoId={}, sku={}, reservado={}, restante={})",
                    request.pedidoId(), sku, request.quantidade(), item.quantidade());
        }

        String reservaId = UUID.randomUUID().toString();
        reservas.put(reservaId, new Reserva(reservaId, sku, request.quantidade(), request.pedidoId()));
        log.info("Reserva registrada em memoria (pedidoId={}, reservaId={}, sku={}, quantidade={})",
                request.pedidoId(), reservaId, sku, request.quantidade());
        return new ReservaResponse(reservaId, "RESERVADO", sku, request.quantidade(), request.pedidoId());
    }

    /**
     * Cancela uma reserva de estoque existente e restaura a quantidade disponivel.
     *
     * @param reservaId identificador da reserva a ser cancelada
     * @return {@code true} se a reserva foi cancelada com sucesso, {@code false} se nao encontrada
     */
    public boolean cancelarReserva(String reservaId) {
        log.info("Tentando cancelar reserva (reservaId={})", reservaId);
        Reserva reserva = reservas.remove(reservaId);
        if (reserva == null) {
            log.warn("Nao foi possivel cancelar reserva porque ela nao existe (reservaId={})", reservaId);
            return false;
        }

        Item item = itens.get(reserva.sku());
        if (item != null) {
            synchronized (reserva.sku().intern()) {
                item.incrementar(reserva.quantidade());
            }
            log.info("Estoque restaurado apos cancelamento (reservaId={}, sku={}, quantidadeRestaurada={})",
                    reservaId, reserva.sku(), reserva.quantidade());
        }
        log.info("Reserva removida com sucesso (reservaId={}, sku={})", reservaId, reserva.sku());
        return true;
    }

    /**
     * Registro interno de uma reserva de estoque.
     *
     * @param id         identificador da reserva
     * @param sku        codigo do produto
     * @param quantidade quantidade reservada
     * @param pedidoId   identificador do pedido
     */
    private record Reserva(String id, String sku, int quantidade, String pedidoId) {
    }

    /**
     * Item de estoque com controle de quantidade.
     */
    private static final class Item {
        private final String sku;
        private final String descricao;
        private int quantidade;

        /**
         * Construtor do item de estoque.
         *
         * @param sku        codigo do produto
         * @param descricao  descricao do produto
         * @param quantidade quantidade disponivel
         */
        private Item(String sku, String descricao, int quantidade) {
            this.sku = sku;
            this.descricao = descricao;
            this.quantidade = quantidade;
        }

        /**
         * Retorna o SKU do item.
         *
         * @return codigo do produto
         */
        private String sku() {
            return sku;
        }

        /**
         * Retorna a descricao do item.
         *
         * @return descricao do produto
         */
        private String descricao() {
            return descricao;
        }

        /**
         * Retorna a quantidade disponivel do item.
         *
         * @return quantidade em estoque
         */
        private int quantidade() {
            return quantidade;
        }

        /**
         * Decrementa a quantidade disponivel do item.
         *
         * @param q quantidade a decrementar
         */
        private void decrementar(int q) {
            this.quantidade -= q;
        }

        /**
         * Incrementa a quantidade disponivel do item.
         *
         * @param q quantidade a incrementar
         */
        private void incrementar(int q) {
            this.quantidade += q;
        }
    }
}
