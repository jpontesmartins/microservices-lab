package com.example.vendas.integration;

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
