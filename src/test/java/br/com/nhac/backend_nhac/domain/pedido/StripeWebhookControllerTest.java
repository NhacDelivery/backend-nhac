package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.PedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcControllerTest(
    controllers = StripeWebhookController.class,
    properties = {"stripe.webhook.secret=whsec_test_secret_aqui"}
)
@AutoConfigureMockMvc(addFilters = false)
public class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    // Ignora a segurança apenas para o Webhook no ambiente de teste de Controller
    @TestConfiguration
    static class SecurityConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request se a assinatura do Stripe for inválida")
    void deveRetornar400SeAssinaturaInvalida() throws Exception {
        String payload = "{\"type\": \"payment_intent.payment_failed\"}";
        String signature = "t=123,v1=assinatura_invalida";

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", signature)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request se faltar o header de assinatura")
    void deveRetornar400SeFaltarHeader() throws Exception {
        String payload = "{\"type\": \"payment_intent.payment_failed\"}";

        // A versão do StripeWebhookController pode ou não exigir o Header.
        // O Webhook.constructEvent joga IllegalArgumentException se o header for nulo ou vazio, 
        // mas o Spring pode rejeitar antes se houver @RequestHeader sem required=false.
        // No StripeWebhookController atual, @RequestHeader("Stripe-Signature") String sigHeader
        // é exigido por padrão, então lançará 400.
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar 200 OK para webhook payment_intent.succeeded valido")
    void deveRetornar200ParaWebhookValido() throws Exception {
        String payload = eventoAssinado("payment_intent.succeeded");
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", assinatura(payload))
                .content(payload))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(pedidoService).marcarComoPagoPorPaymentIntentId("pi_12345");
    }

    @Test
    @DisplayName("Falha assinada permite nova tentativa sem marcar o pedido como pago")
    void falhaAssinadaNaoConfirmaPagamento() throws Exception {
        String payload = eventoAssinado("payment_intent.payment_failed");
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", assinatura(payload))
                .content(payload))
                .andExpect(status().isOk());
        org.mockito.Mockito.verifyNoInteractions(pedidoService);
    }

    private String eventoAssinado(String tipo) {
        return """
                {"id":"evt_e2e","object":"event","api_version":"%s", "type":"%s",
                 "data":{"object":{"id":"pi_12345","object":"payment_intent"}}}
                """.formatted(com.stripe.Stripe.API_VERSION, tipo);
    }

    private String assinatura(String payload) throws Exception {
        long timestamp = java.time.Instant.now().getEpochSecond();
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "whsec_test_secret_aqui".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((timestamp + "." + payload)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(signature);
    }
}
