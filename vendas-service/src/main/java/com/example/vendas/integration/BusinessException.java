package com.example.vendas.integration;

/**
 * Exceção para erros de negócio (respostas HTTP 4xx dos serviços integrados).
 * Utilizada nos fallbacks dos circuit breakers para propagar erros que não são transitórios.
 */
public class BusinessException extends RuntimeException {

    private final String status;

    /**
     * Cria uma nova BusinessException.
     *
     * @param status código do status de erro de negócio
     * @param cause  exceção original que causou o erro
     */
    public BusinessException(String status, Throwable cause) {
        super("Erro de negocio: " + status, cause);
        this.status = status;
    }

    /**
     * Retorna o código do status de erro de negócio.
     *
     * @return codigo do status
     */
    public String getStatus() {
        return status;
    }
}
