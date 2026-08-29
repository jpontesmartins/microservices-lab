package com.example.vendas.shared.exception;

/**
 * Excecao para erros de negocio (respostas HTTP 4xx dos servicos integrados).
 * Utilizada nos fallbacks dos circuit breakers para propagar erros que nao sao transitorios.
 */
public class BusinessException extends RuntimeException {

    private final String status;

    public BusinessException(String status, Throwable cause) {
        super("Erro de negocio: " + status, cause);
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
