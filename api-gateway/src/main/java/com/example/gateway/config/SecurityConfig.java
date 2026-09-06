package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Configuração de segurança do API Gateway (WebFlux / Reativo).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar tokens JWT emitidos pelo Keycloak (realm {@code microservices})</li>
 *   <li>Extrair roles do claim {@code roles} do JWT e mapear para {@code ROLE_}</li>
 *   <li>Permitir acesso sem autenticação aos endpoints {@code /whoami/**} e {@code /actuator/**}</li>
 *   <li>Exigir autenticação para todas as demais requisições</li>
 * </ul>
 *
 * <p>Stack: Spring Security WebFlux + ReactiveJwtDecoder + ReactiveJwtAuthenticationConverter.
 * O {@code ReactiveJwtDecoder} é auto-configurado pelo Spring Boot a partir de
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} no application.yml.
 *
 * @see org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Cadeia de filtros de segurança reativa.
     *
     * <p>Desativa CSRF (API stateless), configura sessão STATELESS,
     * define as regras de autorização por path e integra o Resource Server
     * JWT com decoder e conversor de authorities customizados.
     *
     * <p>O {@code ReactiveJwtDecoder} é injetado via auto-configuração do Spring Boot
     * (não hardcoded), lendo a URI do issuer de {@code application.yml}.
     *
     * @param http configuração do ServerHttpSecurity
     * @param decoder decoder JWT auto-configurado via YAML
     * @return {@link SecurityWebFilterChain} construída
     */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/whoami/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(decoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    /**
     * Conversor de JWT para {@link org.springframework.security.core.Authentication} reativo.
     *
     * <p>Extrai o claim {@code roles} (lista de strings) do JWT e gera
     * {@link org.springframework.security.core.authority.SimpleGrantedAuthority}
     * com prefixo {@code ROLE_} (ex: {@code "USER"} → {@code "ROLE_USER"}).
     *
     * @return {@link ReactiveJwtAuthenticationConverter} configurado
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return Flux.empty();
            }
            return Flux.fromIterable(roles.stream()
                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                .toList());
        });
        return converter;
    }
}
