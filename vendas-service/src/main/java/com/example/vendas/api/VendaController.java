package com.example.vendas.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

/**
 * Controller com dados de vendas de exemplo.
 */
@RestController
public class VendaController {

    private static final Logger log = LoggerFactory.getLogger(VendaController.class);

    /**
     * Lista vendas de exemplo (dados estaticos para demonstracao).
     *
     * @return lista de vendas de exemplo
     */
    @GetMapping("/vendas")
    public List<Map<String, Object>> listarVendas() {
        log.info("Listando vendas de exemplo");
        List<Map<String, Object>> vendas = List.of(
                Map.of("id", 1, "cliente", "Alice", "valor", 120.5),
                Map.of("id", 2, "cliente", "Bob", "valor", 300.0)
        );
        log.info("Vendas retornadas com sucesso (quantidade={})", vendas.size());
        return vendas;
    }
}
