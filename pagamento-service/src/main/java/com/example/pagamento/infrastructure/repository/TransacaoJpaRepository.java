package com.example.pagamento.infrastructure.repository;

import com.example.pagamento.infrastructure.entity.TransacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransacaoJpaRepository extends JpaRepository<TransacaoEntity, String> {

    Optional<TransacaoEntity> findByPedidoId(String pedidoId);

    boolean existsByPedidoId(String pedidoId);
}
