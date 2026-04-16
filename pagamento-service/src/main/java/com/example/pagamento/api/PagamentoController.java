package com.example.pagamento.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PagamentoController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "OK",
                "mensagem", "Pagamentos disponíveis",
                "provedor", "Stripe-sandbox"
        );
    }
}
