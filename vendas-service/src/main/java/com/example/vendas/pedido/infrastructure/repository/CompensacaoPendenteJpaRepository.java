package com.example.vendas.pedido.infrastructure.repository;

import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import com.example.vendas.pedido.domain.model.StatusCompensacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompensacaoPendenteJpaRepository extends JpaRepository<CompensacaoPendenteEntity, Long> {

    List<CompensacaoPendenteEntity> findByStatusOrderByCreatedAtAsc(StatusCompensacao status);

    @Query("SELECT c FROM CompensacaoPendenteEntity c WHERE c.status = :status AND c.attempts < c.maxAttempts ORDER BY c.createdAt ASC")
    List<CompensacaoPendenteEntity> findPendentesParaProcessar(@Param("status") StatusCompensacao status);
}
