package com.example.vendas.pedido.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignTokenPropagator implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignTokenPropagator.class);

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String authHeader = attrs.getRequest().getHeader("Authorization");
            if (authHeader != null && !authHeader.isBlank()) {
                template.header("Authorization", authHeader);
                log.debug("Token propagado para downstream service via Feign");
            } else {
                log.warn("Header Authorization nao encontrado na request original");
            }
        } else {
            log.warn("ServletRequestAttributes nao disponível - token nao propagado");
        }
    }
}
