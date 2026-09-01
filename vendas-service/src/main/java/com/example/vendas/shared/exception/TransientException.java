package com.example.vendas.shared.exception;

public class TransientException extends RuntimeException {

    private final String userMessage;

    public TransientException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
