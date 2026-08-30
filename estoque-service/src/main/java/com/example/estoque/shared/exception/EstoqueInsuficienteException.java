package com.example.estoque.shared.exception;

public class EstoqueInsuficienteException extends IllegalStateException {

    public EstoqueInsuficienteException(String sku, int disponivel) {
        super("Sem estoque para SKU " + sku + " (disp=" + disponivel + ")");
    }
}
