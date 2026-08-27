package com.example.transportadora.api;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller para identificação da instância do serviço.
 */
@RestController
public class WhoamiController {

    private static final Logger log = LoggerFactory.getLogger(WhoamiController.class);

    @Value("${spring.application.name}")
    private String app;

    @Value("${eureka.instance.instance-id:unknown}")
    private String instanceId;

    @Value("${server.port}")
    private int port;

    /**
     * Retorna informações da instância do serviço (nome, instanceId, porta).
     *
     * @return mapa com os dados da instancia
     */
    @GetMapping("/transportadora/whoami")
    public Map<String, Object> whoami() {
        log.info("Whoami consultado (service={}, instanceId={}, port={})", app, instanceId, port);
        return Map.of(
                "service", app,
                "instanceId", instanceId,
                "port", port
        );
    }
}
