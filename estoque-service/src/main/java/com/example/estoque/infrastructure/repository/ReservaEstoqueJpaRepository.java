package com.example.estoque.infrastructure.repository;

import com.example.estoque.infrastructure.entity.ReservaEstoqueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservaEstoqueJpaRepository extends JpaRepository<ReservaEstoqueEntity, String> {

    boolean existsByPedidoIdAndItemSku(String pedidoId, String sku);

    Optional<ReservaEstoqueEntity> findByPedidoIdAndItemSku(String pedidoId, String sku);
}
