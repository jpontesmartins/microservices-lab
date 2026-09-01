package com.example.vendas.pedido.infrastructure.config;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeignTokenPropagatorTest {

    @InjectMocks
    private FeignTokenPropagator propagator;

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("deve propagar header Authorization quando presente na request original")
    void devePropagarHeaderAuthorizationQuandoPresente() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9.test");

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNotNull();
        assertThat(authHeaders).hasSize(1);
        assertThat(authHeaders.iterator().next()).isEqualTo("Bearer eyJhbGciOiJSUzI1NiJ9.test");

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando Authorization nao existe na request")
    void naoDevePropagarQuandoAuthorizationNaoExiste() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("Authorization")).thenReturn(null);

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando Authorization esta em branco")
    void naoDevePropagarQuandoAuthorizationEmBranco() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("Authorization")).thenReturn("   ");

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando RequestContextHolder nao disponivel")
    void naoDevePropagarQuandoRequestContextHolderNaoDisponivel() {
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();
    }
}
