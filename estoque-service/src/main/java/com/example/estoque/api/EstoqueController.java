package com.example.estoque.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class EstoqueController {

    @GetMapping("/itens")
    public List<Map<String, Object>> listarItens() {
        return List.of(
                Map.of("sku", "ABC-123", "descricao", "Teclado Mecânico", "quantidade", 42),
                Map.of("sku", "XYZ-789", "descricao", "Mouse Gamer", "quantidade", 15)
        );
    }
}
