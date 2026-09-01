package com.example.vendas.pedido.infrastructure.config;

import feign.micrometer.MicrometerCapability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTracingConfig {

    @Bean
    public MicrometerCapability micrometerCapability(MeterRegistry registry) {
        return new MicrometerCapability(registry);
    }
}
