package com.example.vendas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Classe principal do microservico de vendas.
 * Configura o Spring Boot e habilita os clientes Feign para integracao com servicos externos.
 */
@SpringBootApplication
@EnableFeignClients
public class VendasServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VendasServiceApplication.class, args);
    }
}
