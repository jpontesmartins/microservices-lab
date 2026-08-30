package com.example.estoque.infrastructure.repository;

import com.example.estoque.infrastructure.entity.ItemEstoqueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemEstoqueJpaRepository extends JpaRepository<ItemEstoqueEntity, String> {
}
