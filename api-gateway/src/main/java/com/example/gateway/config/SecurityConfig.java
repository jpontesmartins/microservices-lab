package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
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
     * @param http configuração do ServerHttpSecurity
     * @return {@link SecurityWebFilterChain} construída
     */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/whoami/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    /**
     * Decoder JWT reativo que valida tokens contra o Keycloak.
     *
     * <p>Obtém as chaves públicas via {@code .well-known/openid-configuration}
     * do issuer {@code http://keycloak:8180/realms/microservices}.
     *
     * @return {@link org.springframework.security.oauth2.jwt.ReactiveJwtDecoder}
     */
    @Bean
    public org.springframework.security.oauth2.jwt.ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(
            "http://keycloak:8180/realms/microservices");
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
