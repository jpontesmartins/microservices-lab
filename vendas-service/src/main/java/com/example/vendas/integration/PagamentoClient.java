package com.example.vendas.integration;

import com.example.vendas.integration.dto.PagamentoRequest;
import com.example.vendas.integration.dto.PagamentoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign para integracao com o pagamento-service.
 */
@FeignClient(name = "pagamento-service")
public interface PagamentoClient {

    /**
     * Processa um pagamento.
     *
     * @param request dados do pagamento a ser processado
     * @return resposta do processamento do pagamento
     */
    @PostMapping("/pagamento/pagamentos")
    PagamentoResponse pagar(@RequestBody PagamentoRequest request);
}

