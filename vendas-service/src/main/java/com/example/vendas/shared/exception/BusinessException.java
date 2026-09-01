package com.example.vendas.shared.exception;

/**
 * Excecao para erros de negocio (respostas HTTP 4xx dos servicos integrados).
 * Utilizada nos fallbacks dos circuit breakers para propagar erros que nao sao transitorios.
 */
public class BusinessException extends RuntimeException {

    private final String status;
    private final String userMessage;

    public BusinessException(String status, Throwable cause) {
        this(status, null, cause);
    }

    public BusinessException(String status, String userMessage, Throwable cause) {
        super("Erro de negocio: " + status, cause);
        this.status = status;
        this.userMessage = userMessage;
    }

    public String getStatus() {
        return status;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
