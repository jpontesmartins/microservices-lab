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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeignTokenPropagatorTest {

    @InjectMocks
    private FeignTokenPropagator propagator;

    @Mock
    private ServiceTokenProvider serviceTokenProvider;

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

        verify(serviceTokenProvider, never()).getToken();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando Authorization nao existe e service token retorna null")
    void naoDevePropagarQuandoAuthorizationNaoExiste() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("Authorization")).thenReturn(null);
        when(serviceTokenProvider.getToken()).thenReturn(null);

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando Authorization esta em branco e service token retorna null")
    void naoDevePropagarQuandoAuthorizationEmBranco() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("Authorization")).thenReturn("   ");
        when(serviceTokenProvider.getToken()).thenReturn(null);

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("nao deve propagar header quando RequestContextHolder nao disponivel e service token retorna null")
    void naoDevePropagarQuandoRequestContextHolderNaoDisponivel() {
        RequestContextHolder.resetRequestAttributes();

        when(serviceTokenProvider.getToken()).thenReturn(null);

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNullOrEmpty();
    }

    @Test
    @DisplayName("deve usar service token quando RequestContextHolder nao disponivel")
    void deveUsarServiceTokenQuandoRequestContextHolderNaoDisponivel() {
        RequestContextHolder.resetRequestAttributes();

        when(serviceTokenProvider.getToken()).thenReturn("Bearer service-token-123");

        RequestTemplate template = new RequestTemplate();
        propagator.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).isNotNull();
        assertThat(authHeaders).hasSize(1);
        assertThat(authHeaders.iterator().next()).isEqualTo("Bearer service-token-123");

        verify(serviceTokenProvider).getToken();
    }
}
