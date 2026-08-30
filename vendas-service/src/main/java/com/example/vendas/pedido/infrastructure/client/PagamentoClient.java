package com.example.vendas.pedido.infrastructure.client;

import com.example.vendas.pedido.infrastructure.dto.PagamentoRequest;
import com.example.vendas.pedido.infrastructure.dto.PagamentoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "pagamento-service")
public interface PagamentoClient {

    @PostMapping("/pagamento/pagamentos")
    PagamentoResponse pagar(@RequestBody PagamentoRequest request);
}
