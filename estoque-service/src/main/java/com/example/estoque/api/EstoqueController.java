package com.example.estoque.api;

import com.example.estoque.core.EstoqueCore;
import com.example.estoque.core.dto.ItemEstoqueResponse;
import com.example.estoque.core.dto.ReservaRequest;
import com.example.estoque.core.dto.ReservaResponse;
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

    private final EstoqueCore estoque;

    public EstoqueController(EstoqueCore estoque) {
        this.estoque = estoque;
    }

    @GetMapping("/itens")
    public List<ItemEstoqueResponse> listarItens() {
        // Mantemos /itens por compatibilidade, mas o caminho recomendado via gateway e /estoque/itens.
        return estoque.listarItens();
    }

    @GetMapping("/estoque/itens")
    public List<ItemEstoqueResponse> listarItensComPrefixo() {
        return estoque.listarItens();
    }

    @PostMapping("/estoque/reservas")
    public ReservaResponse reservar(@RequestBody ReservaRequest request) {
        try {
            return estoque.reservar(request);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/estoque/reservas/{reservaId}")
    public void cancelarReserva(@PathVariable String reservaId) {
        boolean ok = estoque.cancelarReserva(reservaId);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada: " + reservaId);
        }
    }
}
