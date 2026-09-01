package com.example.vendas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;

/**
 * Configuração de segurança do vendas-service (Servlet / Spring MVC).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar tokens JWT emitidos pelo Keycloak (realm {@code microservices})</li>
 *   <li>Extrair roles do claim {@code roles} do JWT e mapear para {@code ROLE_}</li>
 *   <li>Permitir acesso sem autenticação aos endpoints {@code /vendas/whoami} e {@code /actuator/**}</li>
 *   <li>Exigir autenticação para todas as demais requisições</li>
 *   <li>Habilitar {@code @PreAuthorize} / {@code @Secured} via {@code @EnableMethodSecurity}</li>
 * </ul>
 *
 * <p>Stack: Spring Security Servlet + JwtDecoder + JwtAuthenticationConverter.
 * O token JWT é propagado para downstream services (estoque, frete, pagamento)
 * via {@link com.example.vendas.pedido.infrastructure.config.FeignTokenPropagator}.
 *
 * @see org.springframework.security.oauth2.jwt.JwtDecoders
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Cadeia de filtros de segurança servlet.
     *
     * <p>Desativa CSRF (API stateless), configura sessão STATELESS,
     * define as regras de autorização por path e integra o Resource Server
     * JWT com decoder e conversor de authorities customizados.
     *
     * @param http configuração do HttpSecurity
     * @return {@link SecurityFilterChain} construída
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/vendas/whoami").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    /**
     * Decoder JWT que valida tokens contra o Keycloak.
     *
     * <p>Obtém as chaves públicas via {@code .well-known/openid-configuration}
     * do issuer {@code http://keycloak:8180/realms/microservices}.
     *
     * @return {@link JwtDecoder} para validação de tokens
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(
            "http://keycloak:8180/realms/microservices");
    }

    /**
     * Conversor de JWT para {@link org.springframework.security.core.Authentication}.
     *
     * <p>Extrai o claim {@code roles} (lista de strings) do JWT e gera
     * {@link org.springframework.security.core.authority.SimpleGrantedAuthority}
     * com prefixo {@code ROLE_} (ex: {@code "USER"} → {@code "ROLE_USER"}).
     *
     * @return {@link JwtAuthenticationConverter} configurado
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                .<GrantedAuthority>map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        });
        return converter;
    }
}
