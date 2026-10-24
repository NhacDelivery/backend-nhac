package br.com.nhac.backend_nhac.infra.security;

import br.com.nhac.backend_nhac.domain.auth.AuthRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Executa antes do controller; erros seguem o mesmo ControllerAdvice da API. */
@Configuration
@org.springframework.context.annotation.Import(AuthRateLimitService.class)
public class AuthRateLimitConfiguration implements WebMvcConfigurer {
    private final AuthRateLimitService limiter;

    public AuthRateLimitConfiguration(AuthRateLimitService limiter) { this.limiter = limiter; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("POST".equals(request.getMethod())) limiter.checar(request.getRequestURI());
                return true;
            }
        }).addPathPatterns("/api/v1/auth/**", "/api/v1/verificacao-telefone/**");
    }
}
