package com.example.estoque.core.dto;

/**
 * Resposta com os dados de um item em estoque.
 *
 * @param sku        código do produto
 * @param descricao  descrição do produto
 * @param quantidade quantidade disponível em estoque
 */
public record ItemEstoqueResponse(String sku, String descricao, int quantidade) {
}

