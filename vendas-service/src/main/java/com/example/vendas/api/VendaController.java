package com.example.vendas.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class VendaController {

    @GetMapping("/vendas")
    public List<Map<String, Object>> listarVendas() {
        return List.of(
                Map.of("id", 1, "cliente", "Alice", "valor", 120.5),
                Map.of("id", 2, "cliente", "Bob", "valor", 300.0)
        );
    }
}
