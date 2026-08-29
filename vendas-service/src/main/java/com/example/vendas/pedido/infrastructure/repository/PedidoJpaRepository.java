package com.example.vendas.pedido.infrastructure.repository;

import com.example.vendas.pedido.entities.PedidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoJpaRepository extends JpaRepository<PedidoEntity, String> {
}
