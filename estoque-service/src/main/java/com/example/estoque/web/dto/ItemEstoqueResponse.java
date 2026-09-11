package com.example.estoque.web.dto;

import java.math.BigDecimal;

public record ItemEstoqueResponse(String sku, String descricao, String imagem, BigDecimal valor, int quantidade) {
}
