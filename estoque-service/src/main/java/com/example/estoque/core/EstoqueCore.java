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

@Service
public class EstoqueCore {

    private static final Logger log = LoggerFactory.getLogger(EstoqueCore.class);

    private final Map<String, Item> itens = new ConcurrentHashMap<>();
    private final Map<String, Reserva> reservas = new ConcurrentHashMap<>();

    public EstoqueCore() {
        // Seed de estoque para o lab.
        itens.put("ABC-123", new Item("ABC-123", "Teclado Mecanico", 42));
        itens.put("XYZ-789", new Item("XYZ-789", "Mouse Gamer", 15));
    }

    public List<ItemEstoqueResponse> listarItens() {
        log.info("Montando lista de itens em estoque");
        List<ItemEstoqueResponse> out = new ArrayList<>();
        for (Item item : itens.values()) {
            out.add(new ItemEstoqueResponse(item.sku(), item.descricao(), item.quantidade()));
        }
        log.info("Lista de itens em estoque pronta (quantidade={})", out.size());
        return out;
    }

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

    private record Reserva(String id, String sku, int quantidade, String pedidoId) {
    }

    private static final class Item {
        private final String sku;
        private final String descricao;
        private int quantidade;

        private Item(String sku, String descricao, int quantidade) {
            this.sku = sku;
            this.descricao = descricao;
            this.quantidade = quantidade;
        }

        private String sku() {
            return sku;
        }

        private String descricao() {
            return descricao;
        }

        private int quantidade() {
            return quantidade;
        }

        private void decrementar(int q) {
            this.quantidade -= q;
        }

        private void incrementar(int q) {
            this.quantidade += q;
        }
    }
}
