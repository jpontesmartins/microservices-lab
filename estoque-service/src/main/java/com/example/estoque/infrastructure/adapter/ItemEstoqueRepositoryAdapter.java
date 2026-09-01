package com.example.estoque.infrastructure.adapter;

import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import com.example.estoque.infrastructure.entity.ItemEstoqueEntity;
import com.example.estoque.infrastructure.repository.ItemEstoqueJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ItemEstoqueRepositoryAdapter implements ItemEstoqueRepositoryPort {

    private final ItemEstoqueJpaRepository jpaRepository;

    public ItemEstoqueRepositoryAdapter(ItemEstoqueJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ItemEstoque> listarTodos() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ItemEstoque> buscarPorSku(String sku) {
        return jpaRepository.findById(sku).map(this::toDomain);
    }

    @Override
    public Optional<ItemEstoque> buscarPorSkuComLock(String sku) {
        return jpaRepository.findBySkuForUpdate(sku).map(this::toDomain);
    }

    @Override
    public ItemEstoque salvar(ItemEstoque item) {
        ItemEstoqueEntity entity = toEntity(item);
        jpaRepository.save(entity);
        return item;
    }

    private ItemEstoque toDomain(ItemEstoqueEntity entity) {
        return new ItemEstoque(entity.getSku(), entity.getDescricao(), entity.getQuantidade());
    }

    private ItemEstoqueEntity toEntity(ItemEstoque item) {
        return new ItemEstoqueEntity(item.getSku(), item.getDescricao(), item.getQuantidade());
    }
}
