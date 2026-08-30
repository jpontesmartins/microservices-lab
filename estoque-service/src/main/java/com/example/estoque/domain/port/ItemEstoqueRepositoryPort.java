package com.example.estoque.domain.port;

import com.example.estoque.domain.model.ItemEstoque;

import java.util.List;
import java.util.Optional;

public interface ItemEstoqueRepositoryPort {

    List<ItemEstoque> listarTodos();

    Optional<ItemEstoque> buscarPorSku(String sku);

    ItemEstoque salvar(ItemEstoque item);
}
