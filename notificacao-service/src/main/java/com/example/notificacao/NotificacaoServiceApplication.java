package com.example.notificacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do microserviço de notificação.
 * Responsável por enviar notificações ao usuário após pagamento confirmado.
 */
@SpringBootApplication
public class NotificacaoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificacaoServiceApplication.class, args);
    }
}
