package com.example.pagamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do microserviço de pagamento.
 * Responsável por processar pagamentos de pedidos.
 */
@SpringBootApplication
public class PagamentoServiceApplication {

    /**
     * Ponto de entrada da aplicacao.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(PagamentoServiceApplication.class, args);
    }
}
