package com.example.estoque.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhoamiController {

    @Value("${spring.application.name}")
    private String app;

    @Value("${eureka.instance.instance-id:unknown}")
    private String instanceId;

    @Value("${server.port}")
    private int port;

    @GetMapping("/estoque/whoami")
    public Map<String, Object> whoami() {
        return Map.of(
                "service", app,
                "instanceId", instanceId,
                "port", port
        );
    }
}

