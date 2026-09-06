package com.example.vendas.pedido.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    private final RestTemplate restTemplate;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile long tokenExpiresAt;

    public ServiceTokenProvider(
            @Value("${service-token.keycloak-url:http://keycloak:8180}") String keycloakUrl,
            @Value("${service-token.realm:microservices}") String realm,
            @Value("${service-token.client-id:vendas-service}") String clientId,
            @Value("${service-token.client-secret}") String clientSecret) {
        this.tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate.setRequestFactory(factory);
    }

    public synchronized String getToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cachedToken;
        }

        log.info("Obtendo token via client_credentials (clientId={})", clientId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("access_token")) {
                cachedToken = "Bearer " + responseBody.get("access_token");
                int expiresIn = (int) responseBody.getOrDefault("expires_in", 300);
                tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 30) * 1000L;
                log.info("Token obtido com sucesso, expira em {}s", expiresIn);
                return cachedToken;
            }
        } catch (Exception e) {
            log.error("Falha ao obter token via client_credentials: {}", e.getMessage());
        }

        return null;
    }
}
