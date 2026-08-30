package com.example.estoque.application;

import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.model.ReservaEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import com.example.estoque.domain.port.ReservaEstoqueRepositoryPort;
import com.example.estoque.shared.exception.EstoqueInsuficienteException;
import com.example.estoque.shared.exception.SkuDesconhecidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EstoqueService {

    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);

    private final ItemEstoqueRepositoryPort itemRepository;
    private final ReservaEstoqueRepositoryPort reservaRepository;

    public EstoqueService(ItemEstoqueRepositoryPort itemRepository,
                          ReservaEstoqueRepositoryPort reservaRepository) {
        this.itemRepository = itemRepository;
        this.reservaRepository = reservaRepository;
    }

    public List<ItemEstoque> listarItens() {
        log.info("Listando itens em estoque");
        List<ItemEstoque> itens = itemRepository.listarTodos();
        log.info("Itens listados com sucesso (quantidade={})", itens.size());
        return itens;
    }

    @Transactional
    public ReservaEstoque reservar(String pedidoId, String sku, int quantidade) {
        if (pedidoId == null || pedidoId.isBlank()) {
            throw new IllegalArgumentException("pedidoId obrigatorio");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku obrigatorio");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser > 0");
        }

        String skuFinal = sku.trim();
        log.info("Processando reserva de estoque (pedidoId={}, sku={}, quantidade={})", pedidoId, skuFinal, quantidade);

        ItemEstoque item = itemRepository.buscarPorSku(skuFinal)
                .orElseThrow(() -> {
                    log.warn("SKU desconhecido informado na reserva (pedidoId={}, sku={})", pedidoId, skuFinal);
                    return new SkuDesconhecidoException(skuFinal);
                });

        if (item.getQuantidade() < quantidade) {
            log.warn("Estoque insuficiente para reserva (pedidoId={}, sku={}, disponivel={}, solicitado={})",
                    pedidoId, skuFinal, item.getQuantidade(), quantidade);
            throw new EstoqueInsuficienteException(skuFinal, item.getQuantidade());
        }

        item.decrementar(quantidade);
        itemRepository.salvar(item);

        String reservaId = UUID.randomUUID().toString();
        ReservaEstoque reserva = new ReservaEstoque(reservaId, skuFinal, quantidade, pedidoId);
        reservaRepository.salvar(reserva);

        log.info("Reserva registrada com sucesso (pedidoId={}, reservaId={}, sku={}, quantidade={})",
                pedidoId, reservaId, sku, quantidade);
        return reserva;
    }

    @Transactional
    public boolean cancelarReserva(String reservaId) {
        log.info("Tentando cancelar reserva (reservaId={})", reservaId);

        ReservaEstoque reserva = reservaRepository.buscarPorId(reservaId).orElse(null);
        if (reserva == null) {
            log.warn("Nao foi possivel cancelar reserva porque ela nao existe (reservaId={})", reservaId);
            return false;
        }

        String sku = reserva.getSku();
        int quantidade = reserva.getQuantidade();

        itemRepository.buscarPorSku(sku).ifPresent(item -> {
            item.incrementar(quantidade);
            itemRepository.salvar(item);
        });

        reservaRepository.removerPorId(reservaId);
        log.info("Reserva cancelada com sucesso (reservaId={}, sku={})", reservaId, sku);
        return true;
    }
}
