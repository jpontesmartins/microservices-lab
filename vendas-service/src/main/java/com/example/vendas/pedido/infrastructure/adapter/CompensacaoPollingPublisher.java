package com.example.vendas.pedido.infrastructure.adapter;

import com.example.vendas.pedido.domain.model.StatusCompensacao;
import com.example.vendas.pedido.domain.model.TipoCompensacao;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort;
import com.example.vendas.pedido.domain.port.CompensacaoRepositoryPort.CompensacaoPendente;
import com.example.vendas.pedido.infrastructure.client.EstoqueClient;
import com.example.vendas.pedido.infrastructure.client.FreteClient;
import com.example.vendas.pedido.infrastructure.repository.CompensacaoPendenteJpaRepository;
import com.example.vendas.pedido.entities.CompensacaoPendenteEntity;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class CompensacaoPollingPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompensacaoPollingPublisher.class);

    private final CompensacaoPendenteJpaRepository jpaRepository;
    private final EstoqueClient estoqueClient;
    private final FreteClient freteClient;

    public CompensacaoPollingPublisher(CompensacaoPendenteJpaRepository jpaRepository,
            EstoqueClient estoqueClient, FreteClient freteClient) {
        this.jpaRepository = jpaRepository;
        this.estoqueClient = estoqueClient;
        this.freteClient = freteClient;
    }

    @Scheduled(fixedDelayString = "${compensacao.polling-interval:5000}",
            initialDelayString = "${compensacao.initial-delay:2000}")
    @Transactional
    public void processarCompensacoesPendentes() {
        List<CompensacaoPendenteEntity> pendentes = jpaRepository
                .findPendentesParaProcessar(StatusCompensacao.PENDENTE);

        if (pendentes.isEmpty()) {
            return;
        }

        log.info("Polling compensacoes: {} pendentes encontradas", pendentes.size());

        List<Long> enviados = new ArrayList<>();
        List<Long> falhas = new ArrayList<>();

        for (CompensacaoPendenteEntity compensacao : pendentes) {
            try {
                boolean sucesso = executarCompensacao(compensacao);

                if (sucesso) {
                    compensacao.marcarEnviado();
                    enviados.add(compensacao.getId());
                    log.info("Compensacao enviada com sucesso (id={}, tipo={}, refId={}, pedidoId={})",
                            compensacao.getId(), compensacao.getTipo(), compensacao.getRefId(),
                            compensacao.getPedidoId());
                } else {
                    compensacao.incrementarAttempts("Recurso nao encontrado (ja cancelado)");
                    if (compensacao.getAttempts() >= compensacao.getMaxAttempts()) {
                        compensacao.marcarFalha();
                        falhas.add(compensacao.getId());
                        log.warn("Compensacao marcada como FALHA apos {} tentativas (id={}, tipo={}, refId={})",
                                compensacao.getAttempts(), compensacao.getId(),
                                compensacao.getTipo(), compensacao.getRefId());
                    } else {
                        log.warn("Compensacao tentativa {}/{} (id={}, tipo={}, refId={})",
                                compensacao.getAttempts(), compensacao.getMaxAttempts(),
                                compensacao.getId(), compensacao.getTipo(), compensacao.getRefId());
                    }
                }
            } catch (FeignException fe) {
                tratarErroFeign(compensacao, fe, enviados, falhas);
            } catch (Exception e) {
                compensacao.incrementarAttempts(e.toString());
                if (compensacao.getAttempts() >= compensacao.getMaxAttempts()) {
                    compensacao.marcarFalha();
                    falhas.add(compensacao.getId());
                    log.error("Compensacao marcada como FALHA apos {} tentativas (id={}, tipo={}, refId={})",
                            compensacao.getAttempts(), compensacao.getId(),
                            compensacao.getTipo(), compensacao.getRefId(), e);
                } else {
                    log.warn("Compensacao tentativa {}/{} falhou (id={}, tipo={}, refId={}): {}",
                            compensacao.getAttempts(), compensacao.getMaxAttempts(),
                            compensacao.getId(), compensacao.getTipo(), compensacao.getRefId(),
                            e.toString());
                }
            }
        }

        jpaRepository.saveAll(pendentes);

        if (!enviados.isEmpty()) {
            log.info("Compensacoes enviadas: {}", enviados.size());
        }
        if (!falhas.isEmpty()) {
            log.warn("Compensacoes com FALHA permanente: {}", falhas.size());
        }
    }

    private boolean executarCompensacao(CompensacaoPendenteEntity compensacao) {
        return switch (compensacao.getTipo()) {
            case ESTOQUE -> cancelarReserva(compensacao.getRefId());
            case FRETE -> cancelarFrete(compensacao.getRefId());
        };
    }

    private boolean cancelarReserva(String reservaId) {
        try {
            estoqueClient.cancelarReserva(reservaId);
            return true;
        } catch (FeignException fe) {
            if (fe.status() == 404) {
                log.debug("Reserva ja cancelada ou inexistente (reservaId={})", reservaId);
                return true;
            }
            throw fe;
        }
    }

    private boolean cancelarFrete(String freteId) {
        try {
            freteClient.cancelar(freteId);
            return true;
        } catch (FeignException fe) {
            if (fe.status() == 404) {
                log.debug("Frete ja cancelado ou inexistente (freteId={})", freteId);
                return true;
            }
            throw fe;
        }
    }

    private void tratarErroFeign(CompensacaoPendenteEntity compensacao, FeignException fe,
            List<Long> enviados, List<Long> falhas) {
        if (fe.status() == 404) {
            compensacao.marcarEnviado();
            enviados.add(compensacao.getId());
            log.debug("Compensacao tratada como ja enviada via 404 (id={}, refId={})",
                    compensacao.getId(), compensacao.getRefId());
            return;
        }

        compensacao.incrementarAttempts("HTTP " + fe.status() + ": " + fe.getMessage());
        if (compensacao.getAttempts() >= compensacao.getMaxAttempts()) {
            compensacao.marcarFalha();
            falhas.add(compensacao.getId());
            log.warn("Compensacao marcada como FALHA apos {} tentativas (id={}, tipo={}, refId={}, httpStatus={})",
                    compensacao.getAttempts(), compensacao.getId(),
                    compensacao.getTipo(), compensacao.getRefId(), fe.status());
        } else {
            log.warn("Compensacao tentativa {}/{} falhou com HTTP {} (id={}, tipo={}, refId={})",
                    compensacao.getAttempts(), compensacao.getMaxAttempts(), fe.status(),
                    compensacao.getId(), compensacao.getTipo(), compensacao.getRefId());
        }
    }

    /**
     * Processa uma compensacao especifica por ID (usado pelo endpoint admin de retry manual).
     */
    @Transactional
    public boolean processarCompensacaoManual(Long compensacaoId) {
        return jpaRepository.findById(compensacaoId)
                .map(compensacao -> {
                    try {
                        boolean sucesso = executarCompensacao(compensacao);
                        if (sucesso) {
                            compensacao.marcarEnviado();
                        } else {
                            compensacao.incrementarAttempts("Retry manual: recurso nao encontrado");
                        }
                        jpaRepository.save(compensacao);
                        return sucesso;
                    } catch (Exception e) {
                        compensacao.incrementarAttempts("Retry manual: " + e.toString());
                        jpaRepository.save(compensacao);
                        return false;
                    }
                })
                .orElse(false);
    }
}
