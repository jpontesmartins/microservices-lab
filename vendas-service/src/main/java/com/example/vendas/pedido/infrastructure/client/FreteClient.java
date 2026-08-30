package com.example.vendas.pedido.infrastructure.client;

import com.example.vendas.pedido.infrastructure.dto.FreteRequest;
import com.example.vendas.pedido.infrastructure.dto.FreteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "frete-service")
public interface FreteClient {

    @PostMapping("/frete/calcular")
    FreteResponse calcular(@RequestBody FreteRequest request);

    @DeleteMapping("/frete/calcular/{freteId}")
    void cancelar(@PathVariable String freteId);
}
