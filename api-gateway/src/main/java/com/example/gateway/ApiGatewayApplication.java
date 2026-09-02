package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do API Gateway.
 * Ponto de entrada unico para todos os microservicos do ecossistema.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Ponto de entrada da aplicacao.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
