package com.example.vendas.pedido.web.controller;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.infrastructure.adapter.CompensacaoPollingPublisher;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vendas/compensacoes")
public class CompensacaoController {

    private static final Logger log = LoggerFactory.getLogger(CompensacaoController.class);

    private final CompensacaoPendenteJpaRepository jpaRepository;
    private final CompensacaoPollingPublisher pollingPublisher;

    public CompensacaoController(CompensacaoPendenteJpaRepository jpaRepository,
            CompensacaoPollingPublisher pollingPublisher) {
        this.jpaRepository = jpaRepository;
        this.pollingPublisher = pollingPublisher;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar() {
        log.info("Consulta de compensacoes pendentes");
        List<CompensacaoPendenteEntity> pendentes = jpaRepository
                .findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE);

        List<Map<String, Object>> result = pendentes.stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "pedidoId", c.getPedidoId(),
                        "tipo", c.getTipo().name(),
                        "refId", c.getRefId(),
                        "status", c.getStatus().name(),
                        "attempts", c.getAttempts(),
                        "maxAttempts", c.getMaxAttempts(),
                        "lastError", c.getLastError() != null ? c.getLastError() : "",
                        "createdAt", c.getCreatedAt().toString()))
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable Long id) {
        log.info("Retry manual de compensacao (id={})", id);
        boolean sucesso = pollingPublisher.processarCompensacaoManual(id);

        if (sucesso) {
            return ResponseEntity.ok(Map.of(
                    "message", "Compensacao processada com sucesso",
                    "id", id));
        } else {
            return ResponseEntity.ok(Map.of(
                    "message", "Compensacao nao processada (erro ou nao encontrada)",
                    "id", id));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long pendentes = jpaRepository.findByStatusOrderByCreatedAtAsc(StatusCompensacao.PENDENTE).size();
        long enviados = jpaRepository.findByStatusOrderByCreatedAtAsc(StatusCompensacao.ENVIADO).size();
        long falhas = jpaRepository.findByStatusOrderByCreatedAtAsc(StatusCompensacao.FALHA).size();

        return ResponseEntity.ok(Map.of(
                "pendentes", pendentes,
                "enviados", enviados,
                "falhas", falhas));
    }
}
