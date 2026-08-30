package com.example.estoque.infrastructure.adapter;

import com.example.estoque.domain.model.ReservaEstoque;
import com.example.estoque.domain.port.ReservaEstoqueRepositoryPort;
import com.example.estoque.infrastructure.entity.ItemEstoqueEntity;
import com.example.estoque.infrastructure.entity.ReservaEstoqueEntity;
import com.example.estoque.infrastructure.repository.ItemEstoqueJpaRepository;
import com.example.estoque.infrastructure.repository.ReservaEstoqueJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ReservaEstoqueRepositoryAdapter implements ReservaEstoqueRepositoryPort {

    private final ReservaEstoqueJpaRepository jpaRepository;
    private final ItemEstoqueJpaRepository itemEstoqueJpaRepository;

    public ReservaEstoqueRepositoryAdapter(ReservaEstoqueJpaRepository jpaRepository,
                                           ItemEstoqueJpaRepository itemEstoqueJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.itemEstoqueJpaRepository = itemEstoqueJpaRepository;
    }

    @Override
    public ReservaEstoque salvar(ReservaEstoque reserva) {
        ItemEstoqueEntity itemEntity = itemEstoqueJpaRepository.findById(reserva.getSku())
                .orElseThrow(() -> new IllegalArgumentException("SKU nao encontrado: " + reserva.getSku()));
        ReservaEstoqueEntity entity = new ReservaEstoqueEntity(
                reserva.getId(), itemEntity, reserva.getQuantidade(), reserva.getPedidoId());
        jpaRepository.save(entity);
        return reserva;
    }

    @Override
    public Optional<ReservaEstoque> buscarPorId(String id) {
        return jpaRepository.findById(id)
                .map(e -> new ReservaEstoque(e.getId(), e.getItem().getSku(), e.getQuantidade(), e.getPedidoId()));
    }

    @Override
    public void removerPorId(String id) {
        jpaRepository.deleteById(id);
    }
}
