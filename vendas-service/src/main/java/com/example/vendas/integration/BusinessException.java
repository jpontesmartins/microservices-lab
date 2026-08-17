package com.example.vendas.integration;

/**
 * Excecao para erros de negocio (respostas HTTP 4xx dos servicos integrados).
 * Utilizada nos fallbacks dos circuit breakers para propagar erros que nao sao transitórios.
 */
public class BusinessException extends RuntimeException {

    private final String status;

    /**
     * Cria uma nova BusinessException.
     *
     * @param status codigo do status de erro de negocio
     * @param cause  excecao original que causou o erro
     */
    public BusinessException(String status, Throwable cause) {
        super("Erro de negocio: " + status, cause);
        this.status = status;
    }

    /**
     * Retorna o codigo do status de erro de negocio.
     *
     * @return codigo do status
     */
    public String getStatus() {
        return status;
    }
}
