package com.example.estoque.api;

import com.example.estoque.core.EstoqueCore;
import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Controller REST para operações de estoque.
 * Endpoints para listagem, reserva e cancelamento de itens em estoque.
 */
@RestController
public class EstoqueController {

    private static final Logger log = LoggerFactory.getLogger(EstoqueController.class);

    private final EstoqueCore estoque;

    /**
     * Construtor com injecao de dependencia do nucleo de estoque.
     *
     * @param estoque nucleo de logica de estoque
     */
    public EstoqueController(EstoqueCore estoque) {
        this.estoque = estoque;
    }

    /**
     * Lista todos os itens em estoque (endpoint legado sem prefixo).
     *
     * @return lista de itens em estoque
     */
    @GetMapping("/itens")
    public List<ItemEstoqueResponse> listarItens() {
        // Mantemos /itens por compatibilidade, mas o caminho recomendado via gateway e /estoque/itens.
        log.info("Listagem de itens recebida no endpoint legado /itens");
        List<ItemEstoqueResponse> itens = estoque.listarItens();
        log.info("Itens retornados com sucesso (quantidade={})", itens.size());
        return itens;
    }

    /**
     * Lista todos os itens em estoque (endpoint recomendado com prefixo).
     *
     * @return lista de itens em estoque
     */
    @GetMapping("/estoque/itens")
    public List<ItemEstoqueResponse> listarItensComPrefixo() {
        log.info("Listagem de itens recebida no endpoint /estoque/itens");
        List<ItemEstoqueResponse> itens = estoque.listarItens();
        log.info("Itens retornados com sucesso (quantidade={})", itens.size());
        return itens;
    }

    /**
     * Cria uma nova reserva de estoque para um pedido.
     *
     * @param request dados da reserva (pedidoId, sku, quantidade)
     * @return resposta da reserva criada
     * @throws ResponseStatusException BAD_REQUEST se validação falhar, CONFLICT se estoque insuficiente
     */
    @PostMapping("/estoque/reservas")
    public ReservaResponse reservar(@RequestBody ReservaRequest request) {
        log.info("Solicitacao de reserva recebida (pedidoId={}, sku={}, quantidade={})",
                request != null ? request.pedidoId() : null,
                request != null ? request.sku() : null,
                request != null ? request.quantidade() : null);
        try {
            ReservaResponse response = estoque.reservar(request);
            log.info("Reserva concluida com sucesso (pedidoId={}, reservaId={}, sku={}, quantidade={})",
                    response.pedidoId(), response.reservaId(), response.sku(), response.quantidade());
            return response;
        } catch (IllegalStateException e) {
            log.warn("Falha de estoque ao reservar: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Falha de validacao ao reservar estoque: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Cancela uma reserva de estoque existente.
     *
     * @param reservaId identificador da reserva a ser cancelada
     * @throws ResponseStatusException NOT_FOUND se reserva nao existir
     */
    @DeleteMapping("/estoque/reservas/{reservaId}")
    public void cancelarReserva(@PathVariable String reservaId) {
        log.info("Solicitacao de cancelamento de reserva recebida (reservaId={})", reservaId);
        boolean ok = estoque.cancelarReserva(reservaId);
        if (!ok) {
            log.warn("Reserva nao encontrada para cancelamento (reservaId={})", reservaId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada: " + reservaId);
        }
        log.info("Reserva cancelada com sucesso (reservaId={})", reservaId);
    }
}
