package com.example.estoque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do microserviço de estoque.
 * Responsável por gerenciar o estoque de produtos, incluindo reservas e cancelamentos.
 */
@SpringBootApplication
public class EstoqueServiceApplication {

    /**
     * Ponto de entrada da aplicacao.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(EstoqueServiceApplication.class, args);
    }
}
