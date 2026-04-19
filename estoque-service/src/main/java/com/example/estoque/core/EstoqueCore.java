package com.example.estoque.core;

import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EstoqueCore {

    private final Map<String, Item> itens = new ConcurrentHashMap<>();
    private final Map<String, Reserva> reservas = new ConcurrentHashMap<>();

    public EstoqueCore() {
        // Seed de estoque para o lab.
        itens.put("ABC-123", new Item("ABC-123", "Teclado Mecanico", 42));
        itens.put("XYZ-789", new Item("XYZ-789", "Mouse Gamer", 15));
    }

    public List<ItemEstoqueResponse> listarItens() {
        List<ItemEstoqueResponse> out = new ArrayList<>();
        for (Item item : itens.values()) {
            out.add(new ItemEstoqueResponse(item.sku(), item.descricao(), item.quantidade()));
        }
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
        Item item = itens.get(sku);
        if (item == null) {
            throw new IllegalArgumentException("SKU desconhecido: " + sku);
        }

        // Atualizacao atomica por SKU.
        synchronized (sku.intern()) {
            if (item.quantidade() < request.quantidade()) {
                throw new IllegalStateException("Sem estoque para SKU " + sku + " (disp=" + item.quantidade() + ")");
            }
            item.decrementar(request.quantidade());
        }

        String reservaId = UUID.randomUUID().toString();
        reservas.put(reservaId, new Reserva(reservaId, sku, request.quantidade(), request.pedidoId()));
        return new ReservaResponse(reservaId, "RESERVADO", sku, request.quantidade(), request.pedidoId());
    }

    public boolean cancelarReserva(String reservaId) {
        Reserva reserva = reservas.remove(reservaId);
        if (reserva == null) {
            return false;
        }

        Item item = itens.get(reserva.sku());
        if (item != null) {
            synchronized (reserva.sku().intern()) {
                item.incrementar(reserva.quantidade());
            }
        }
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

