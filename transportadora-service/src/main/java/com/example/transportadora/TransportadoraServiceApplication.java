package com.example.transportadora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do microserviço de transportadora.
 * Responsável por processar o envio de pacotes após pagamento confirmado.
 */
@SpringBootApplication
public class TransportadoraServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransportadoraServiceApplication.class, args);
    }
}
