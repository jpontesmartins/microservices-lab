package com.example.vendas.pedido.infrastructure.repository;

import com.example.vendas.pedido.entities.OutboxEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutboxEventoJpaRepository extends JpaRepository<OutboxEventoEntity, Long> {

    List<OutboxEventoEntity> findByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEventoEntity e SET e.published = true WHERE e.id IN :ids")
    void markAsPublished(@Param("ids") List<Long> ids);
}
