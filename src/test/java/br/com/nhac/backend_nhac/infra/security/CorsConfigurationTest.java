package br.com.nhac.backend_nhac.infra.security;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationTest {
    private static final String LOJISTA = "https://nhac-lojista-web.vercel.app";
    private final CorsFilter filter = new CorsFilter(new SecurityConfig(null).corsConfigurationSource());

    @Test
    void permitePreflightDoLoginPublicadoNaVercel() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", LOJISTA);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type,authorization,x-app-origin");
        var response = new MockHttpServletResponse();
        var controllerExecutado = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> controllerExecutado.set(true));
        assertEquals(200, response.getStatus());
        assertEquals(LOJISTA, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
        assertFalse(controllerExecutado.get(), "Preflight não deve executar o login");
    }

    @Test
    void respostaDeLoginInvalidoTambemTemCabecalhoCors() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Origin", LOJISTA);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
        assertEquals(401, response.getStatus());
        assertEquals(LOJISTA, response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void origemNaoAutorizadaContinuaBloqueada() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://origem-nao-autorizada.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Origem não permitida"));
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}
