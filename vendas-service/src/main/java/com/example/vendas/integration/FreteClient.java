package com.example.vendas.integration;

import com.example.vendas.integration.dto.FreteRequest;
import com.example.vendas.integration.dto.FreteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign para integracao com o frete-service.
 */
@FeignClient(name = "frete-service")
public interface FreteClient {

    /**
     * Calcula o frete para um pedido.
     *
     * @param request dados para o calculo do frete
     * @return resposta com o frete calculado
     */
    @PostMapping("/frete/calcular")
    FreteResponse calcular(@RequestBody FreteRequest request);

    /**
     * Cancela um frete calculado.
     *
     * @param freteId identificador do frete a ser cancelado
     */
    @DeleteMapping("/frete/calcular/{freteId}")
    void cancelar(@PathVariable String freteId);
}
