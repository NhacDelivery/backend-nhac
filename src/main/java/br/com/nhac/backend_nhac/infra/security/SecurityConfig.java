package br.com.nhac.backend_nhac.infra.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
// Habilita a aplicação das anotações @PreAuthorize.
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/verificacao-telefone/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/asaas").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll() 
                        .requestMatchers(HttpMethod.GET, "/publicacao/**", "/.well-known/assetlinks.json", "/.well-known/apple-app-site-association").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/lojas/minha-loja").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lojas/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/lojas/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/**").hasAnyRole("LOJISTA", "FUNCIONARIO", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**").permitAll()
                        .requestMatchers("/api/v1/lojista/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos").hasAnyRole("LOJISTA", "FUNCIONARIO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasAnyRole("LOJISTA", "FUNCIONARIO", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/produtos/**").hasAnyRole("LOJISTA", "FUNCIONARIO", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/status").hasAnyRole("ADMIN", "LOJISTA", "FUNCIONARIO")

                        // Matchers de URL complementam as permissões dos controllers.
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/despachar/**").hasAnyRole("ADMIN", "LOJISTA", "FUNCIONARIO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/entregas/ofertas/pendentes").hasAnyRole("ENTREGADOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/ofertas/*/aceitar").hasAnyRole("ENTREGADOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/ofertas/*/recusar").hasAnyRole("ENTREGADOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/entregas/ativa").hasAnyRole("ENTREGADOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/*/coletar").hasAnyRole("ENTREGADOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entregas/*/concluir").hasAnyRole("ENTREGADOR", "ADMIN")
                        // /{pedidoId}/rota fica só authenticated: cliente, loja e
                        // entregador da corrida podem acessar (ownership checado no
                        // controller, não dá pra expressar isso num requestMatcher).
                        .requestMatchers(HttpMethod.GET, "/api/v1/entregas/*/rota").authenticated()
                        .requestMatchers("/api/v1/entregas/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/entregador/cadastro").authenticated()
                        // A regra de conversas exige papel de entregador e deve preceder
                        // o matcher genérico de /api/v1/entregador/**.
                        .requestMatchers("/api/v1/entregador/conversas/**").hasAnyRole("ENTREGADOR", "ADMIN")
                        // Os services validam o vínculo pelo usuário e retornam erro de negócio
                        // quando o perfil de entregador ainda não existe.
                        .requestMatchers("/api/v1/entregador/**").authenticated()

                        .requestMatchers("/ws/**", "/ws-native/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

  @Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    configuration.setAllowedOrigins(List.of(
        "https://nhac-lojista-web.vercel.app",
        "https://supreme-pancake-g49w55w959xh94pv-3000.app.github.dev",
        "https://github.dev",
        "http://localhost:3000"
    ));
    
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Cache-Control", "X-App-Origin", "Idempotency-Key"));
    configuration.setExposedHeaders(List.of("Authorization"));
    configuration.setAllowCredentials(true); 
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
