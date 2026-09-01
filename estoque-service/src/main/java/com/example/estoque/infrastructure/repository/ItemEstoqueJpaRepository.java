package com.example.estoque.infrastructure.repository;

import com.example.estoque.infrastructure.entity.ItemEstoqueEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemEstoqueJpaRepository extends JpaRepository<ItemEstoqueEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ItemEstoqueEntity i WHERE i.sku = :sku")
    Optional<ItemEstoqueEntity> findBySkuForUpdate(@Param("sku") String sku);
}
