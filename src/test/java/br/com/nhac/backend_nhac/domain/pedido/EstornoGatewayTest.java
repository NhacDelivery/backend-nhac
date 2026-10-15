package br.com.nhac.backend_nhac.domain.pedido;

import java.math.BigDecimal;
import java.util.List;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.param.RefundListParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class EstornoGatewayTest {
    Pedido pedido() {
        var p = new Pedido(); p.setId("p1"); p.setValorTotal(new BigDecimal("50.00"));
        p.setAsaasPaymentId("pay"); p.setStripePaymentIntentId("pi"); return p;
    }
    @Test void asaasEnviaValorIntegralEConfirmaPorConsulta() {
        var http = mock(RestTemplate.class);
        var service = new AsaasPaymentService(http, mock(PedidoRepository.class), mock(PagamentoClienteRepository.class));
        ReflectionTestUtils.setField(service, "asaasApiKey", "test");
        ReflectionTestUtils.setField(service, "asaasApiUrl", "https://sandbox.invalid/v3");
        when(http.exchange(eq("https://sandbox.invalid/v3/payments/pay"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"RECEIVED\"}"), ResponseEntity.ok("{\"status\":\"REFUNDED\"}"));
        when(http.postForEntity(eq("https://sandbox.invalid/v3/payments/pay/refund"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        assertEquals(StatusEstorno.CONCLUIDO, service.estornar(pedido()));
        var captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForEntity(eq("https://sandbox.invalid/v3/payments/pay/refund"), captor.capture(), eq(String.class));
        var body = com.google.gson.JsonParser.parseString(captor.getValue().getBody().toString()).getAsJsonObject();
        assertEquals(0, body.get("value").getAsBigDecimal().compareTo(new BigDecimal("50.00")));
        assertEquals("test", captor.getValue().getHeaders().getFirst("access_token"));
    }
    @Test void asaasJaDevolvidoNaoEnviaOutroPost() {
        var http = mock(RestTemplate.class);
        var service = new AsaasPaymentService(http, mock(PedidoRepository.class), mock(PagamentoClienteRepository.class));
        ReflectionTestUtils.setField(service, "asaasApiUrl", "https://sandbox.invalid/v3");
        when(http.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"REFUNDED\"}"));
        assertEquals(StatusEstorno.CONCLUIDO, service.estornar(pedido()));
        verify(http, never()).postForEntity(anyString(), any(), eq(String.class));
    }
    @Test void stripeEnviaCentavosEChaveEstavel() {
        var service = new StripePaymentService(mock(PedidoRepository.class));
        var vazio = new RefundCollection(); vazio.setData(List.of());
        var refund = new Refund(); refund.setId("re"); refund.setPaymentIntent("pi");
        refund.setAmount(5000L); refund.setCurrency("brl"); refund.setStatus("pending");
        try (var stripe = mockStatic(Refund.class)) {
            stripe.when(() -> Refund.list(any(RefundListParams.class))).thenReturn(vazio);
            stripe.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(refund);
            assertEquals(StatusEstorno.PROCESSANDO, service.estornar(pedido()));
            stripe.verify(() -> Refund.create(argThat((RefundCreateParams params) -> params.getAmount() == 5000L
                && "pi".equals(params.getPaymentIntent())), argThat((RequestOptions options) -> "nhac-estorno-p1".equals(options.getIdempotencyKey()))));
        }
    }
    @Test void stripeParcialNaoAfirmaDevolucaoIntegralNemCriaNovoEstorno() {
        var service = new StripePaymentService(mock(PedidoRepository.class));
        var refund = new Refund(); refund.setPaymentIntent("pi"); refund.setAmount(100L); refund.setCurrency("brl"); refund.setStatus("succeeded");
        var lista = new RefundCollection(); lista.setData(List.of(refund)); lista.setHasMore(false);
        try (var stripe = mockStatic(Refund.class)) {
            stripe.when(() -> Refund.list(any(RefundListParams.class))).thenReturn(lista);
            assertEquals(StatusEstorno.REVISAO, service.estornar(pedido()));
            stripe.verify(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)), never());
        }
    }
}
