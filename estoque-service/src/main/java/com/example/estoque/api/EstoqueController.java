package com.example.estoque.api;

import com.example.estoque.application.EstoqueService;
import com.example.estoque.domain.model.ReservaEstoque;
import com.example.estoque.web.dto.ItemEstoqueResponse;
import com.example.estoque.web.dto.ReservaRequest;
import com.example.estoque.web.dto.ReservaResponse;
import com.example.estoque.shared.exception.EstoqueInsuficienteException;
import com.example.estoque.shared.exception.SkuDesconhecidoException;
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

@RestController
public class EstoqueController {

    private static final Logger log = LoggerFactory.getLogger(EstoqueController.class);

    private final EstoqueService estoque;

    public EstoqueController(EstoqueService estoque) {
        this.estoque = estoque;
    }

    @GetMapping("/itens")
    public List<ItemEstoqueResponse> listarItens() {
        log.info("Listagem de itens recebida no endpoint legado /itens");
        List<ItemEstoqueResponse> itens = estoque.listarItens().stream()
                .map(i -> new ItemEstoqueResponse(i.getSku(), i.getDescricao(), i.getQuantidade()))
                .toList();
        log.info("Itens retornados com sucesso (quantidade={})", itens.size());
        return itens;
    }

    @GetMapping("/estoque/itens")
    public List<ItemEstoqueResponse> listarItensComPrefixo() {
        log.info("Listagem de itens recebida no endpoint /estoque/itens");
        List<ItemEstoqueResponse> itens = estoque.listarItens().stream()
                .map(i -> new ItemEstoqueResponse(i.getSku(), i.getDescricao(), i.getQuantidade()))
                .toList();
        log.info("Itens retornados com sucesso (quantidade={})", itens.size());
        return itens;
    }

    @PostMapping("/estoque/reservas")
    public ReservaResponse reservar(@RequestBody ReservaRequest request) {
        log.info("Solicitacao de reserva recebida (pedidoId={}, sku={}, quantidade={})",
                request != null ? request.pedidoId() : null,
                request != null ? request.sku() : null,
                request != null ? request.quantidade() : null);
        try {
            ReservaEstoque reserva = estoque.reservar(request.pedidoId(), request.sku(), request.quantidade());
            ReservaResponse response = new ReservaResponse(
                    reserva.getId(), "RESERVADO", reserva.getSku(), reserva.getQuantidade(), reserva.getPedidoId());
            log.info("Reserva concluida com sucesso (pedidoId={}, reservaId={}, sku={}, quantidade={})",
                    response.pedidoId(), response.reservaId(), response.sku(), response.quantidade());
            return response;
        } catch (EstoqueInsuficienteException e) {
            log.warn("Falha de estoque ao reservar: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (SkuDesconhecidoException e) {
            log.warn("Falha de validacao ao reservar estoque: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Falha de validacao ao reservar estoque: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

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
