package com.example.estoque.shared.exception;

public class SkuDesconhecidoException extends IllegalArgumentException {

    public SkuDesconhecidoException(String sku) {
        super("SKU desconhecido: " + sku);
    }
}
