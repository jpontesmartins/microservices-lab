package com.example.estoque.infrastructure.repository;

import com.example.estoque.infrastructure.entity.ReservaEstoqueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaEstoqueJpaRepository extends JpaRepository<ReservaEstoqueEntity, String> {
}
