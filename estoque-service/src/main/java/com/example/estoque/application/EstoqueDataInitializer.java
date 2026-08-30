package com.example.estoque.application;

import com.example.estoque.domain.model.ItemEstoque;
import com.example.estoque.domain.port.ItemEstoqueRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EstoqueDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EstoqueDataInitializer.class);

    private final ItemEstoqueRepositoryPort itemRepository;

    public EstoqueDataInitializer(ItemEstoqueRepositoryPort itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(String... args) {
        List<ItemEstoque> existentes = itemRepository.listarTodos();
        if (existentes.isEmpty()) {
            log.info("Populando estoque com dados seed");
            itemRepository.salvar(new ItemEstoque("ABC-123", "Teclado Mecanico", 42));
            itemRepository.salvar(new ItemEstoque("XYZ-789", "Mouse Gamer", 15));
            itemRepository.salvar(new ItemEstoque("DEF-456", "Monitor 27pol", 10));
            itemRepository.salvar(new ItemEstoque("GHI-012", "Webcam Full HD", 25));
            itemRepository.salvar(new ItemEstoque("JKL-345", "Headset Gamer", 30));
            log.info("Estoque populado com sucesso");
        } else {
            log.info("Estoque ja possui dados (itens={})", existentes.size());
        }
    }
}
