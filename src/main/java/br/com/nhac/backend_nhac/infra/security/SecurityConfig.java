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
// CORREÇÃO CRÍTICA (V039): esta anotação nunca existiu no projeto. Sem ela,
// TODO @PreAuthorize do código — inclusive os já existentes em
// PedidoController e ProdutoController — é silenciosamente ignorado pelo
// Spring (sem erro nenhum no boot, sem warning). O RBAC "correto" confirmado
// em rodadas anteriores funcionava só porque havia checagem manual de
// ownership dentro dos Services (ex.: LojaAccessService.temAcessoALoja), não
// por causa das anotações. Habilitar isto agora faz toda anotação já escrita
// no projeto passar a valer de verdade — vale reexecutar a suíte de RBAC.
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

                        // ---- Entrega / despacho (V039) ----
                        // Antes, tudo abaixo de /api/v1/entregas/** era só .authenticated():
                        // qualquer conta logada (inclusive CLIENTE) conseguia chamar
                        // /ofertas/pendentes, /aceitar, /coletar etc. Agora que
                        // @EnableMethodSecurity está ativo, os @PreAuthorize dos
                        // controllers passam a valer — mas mantemos os matchers de URL
                        // aqui também como defesa em profundidade (mesmo padrão já usado
                        // para /produtos/**).
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
                        // Chat com a loja só faz sentido pra quem já é
                        // entregador de verdade — mantém a exigência de
                        // role aqui. Precisa vir ANTES da regra genérica de
                        // /api/v1/entregador/** abaixo: o Spring Security
                        // usa a PRIMEIRA regra cujo padrão casar o path, e
                        // "/api/v1/entregador/**" já casa qualquer rota de
                        // conversas também — com a ordem antiga, esta linha
                        // nunca era alcançada (mas o @PreAuthorize no
                        // controller cobria a mesma exigência, então não
                        // chegou a ser uma brecha de segurança).
                        .requestMatchers("/api/v1/entregador/conversas/**").hasAnyRole("ENTREGADOR", "ADMIN")
                        // CORREÇÃO: era hasAnyRole('ENTREGADOR', 'ADMIN') —
                        // isso barrava com um 403 seco do Spring Security
                        // ANTES de chegar no controller, pra QUALQUER
                        // cliente que ainda não tivesse completado o
                        // cadastro de entregador. E os controllers de
                        // /perfil, /status, /localizacao, /ganhos e
                        // /entregas já tratam esse caso corretamente,
                        // lançando uma exceção de negócio com mensagem
                        // clara ("Perfil de entregador não encontrado") via
                        // EntregadorService.buscarPorUsuario — só que essa
                        // mensagem nunca era alcançada. Rebaixar para
                        // authenticated() não abre brecha nova: cada
                        // Service já filtra pelo usuario.getId() de quem
                        // está logado, então ninguém vê dados de outro
                        // entregador de qualquer forma.
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
