package com.example.vendas.integration;

import com.example.vendas.integration.dto.PagamentoRequest;
import com.example.vendas.integration.dto.PagamentoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "pagamento-service")
public interface PagamentoClient {

    @PostMapping("/pagamento/pagamentos")
    PagamentoResponse pagar(@RequestBody PagamentoRequest request);
}

