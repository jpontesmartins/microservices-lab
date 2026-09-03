package com.example.frete.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Configuração de segurança do frete-service (Servlet / Spring MVC).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar tokens JWT emitidos pelo Keycloak (realm {@code microservices})</li>
 *   <li>Extrair roles do claim {@code roles} do JWT e mapear para {@code ROLE_}</li>
 *   <li>Permitir acesso sem autenticação a:
 *     <ul>
 *       <li>{@code /frete/whoami} — identificação da instância</li>
 *       <li>{@code /actuator/**} — health checks e métricas Prometheus</li>
 *     </ul>
 *   </li>
 *   <li>Exigir autenticação para cálculo ({@code POST /frete/calcular}),
 *       cancelamento ({@code DELETE /frete/calcular/{id}} e
 *       {@code POST /frete/calcular/{id}/cancelar})</li>
 *   <li>Habilitar {@code @PreAuthorize} via {@code @EnableMethodSecurity}</li>
 * </ul>
 *
 * <p>Stack: Spring Security Servlet + JwtDecoder + JwtAuthenticationConverter.
 * Recebe tokens propagados pelo vendas-service via Feign.
 * Estado interno gerenciado via {@link java.util.concurrent.ConcurrentHashMap} (sem banco de dados).
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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/frete/whoami").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

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
