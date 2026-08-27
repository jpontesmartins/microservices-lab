package com.example.frete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do microserviço de frete.
 * Responsável por calcular o valor e prazo de entrega de pedidos.
 */
@SpringBootApplication
public class FreteServiceApplication {

    /**
     * Ponto de entrada da aplicacao.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(FreteServiceApplication.class, args);
    }
}
