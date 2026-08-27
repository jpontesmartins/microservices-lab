package com.example.vendas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Classe principal do microserviço de vendas.
 * Configura o Spring Boot e habilita os clientes Feign para integração com serviços externos.
 */
@SpringBootApplication
@EnableFeignClients
public class VendasServiceApplication {

    /**
     * Ponto de entrada da aplicacao.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(VendasServiceApplication.class, args);
    }
}
