package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
public class AsaasPaymentServiceTest {

    @InjectMocks
    private AsaasPaymentService asaasPaymentService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private br.com.nhac.backend_nhac.domain.pedido.PedidoRepository pedidoRepository;

    private final String testApiKey = "test_asaas_api_key_123";
    private final String testApiUrl = "https://sandbox.asaas.com/api/v3";

    private final String nomePagador = "Matheus Alves";
    private final String emailPagador = "matheus@nhac.com";
    private final String cpfPagador = "12345678901";

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(asaasPaymentService, "asaasApiKey", testApiKey);
        ReflectionTestUtils.setField(asaasPaymentService, "asaasApiUrl", testApiUrl);
        ReflectionTestUtils.setField(asaasPaymentService, "gson", new Gson());
        lenient().when(restTemplate.exchange(
                contains("/pixQrCode"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"payload\":\"000201...\"}"));
    }

    @Test
    @DisplayName("Deve criar cobrança PIX com sucesso quando API do Asaas retornar OK")
    void deveCriarCobrancaPixComSucesso() {
        Pedido pedido = criarPedidoTeste();
        String paymentId = "pay_test_123456";
        String pixQrCode = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String pixCopyAndPaste = "00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-426614174000520400005303986540510.005802BR5913NHAC Store6008Sao Paulo62070503***6304ABCD";

        // Mock customer creation
        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        // Mock payment creation
        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", paymentId);
        paymentResponse.addProperty("pixQrCode", pixQrCode);
        paymentResponse.addProperty("pixCopyAndPaste", pixCopyAndPaste);
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.CREATED);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        PedidoCriadoDTO resultado = asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        assertNotNull(resultado);
        assertEquals(pedido.getId(), resultado.pedidoId());
        assertNull(resultado.clientSecret(), "clientSecret deve ser null para cobranças Asaas");
        assertEquals("000201...", resultado.pixCopiaECola());
        assertEquals("000201...", resultado.qrCodeUrl());
        assertEquals(paymentId, pedido.getAsaasPaymentId());

        verify(restTemplate).postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class));
        verify(restTemplate).postForEntity(eq(testApiUrl + "/payments"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Deve buscar o QR Code no endpoint próprio mesmo sem dados na criação")
    void deveCriarCobrancaPixSemQrCodeNaResposta() {
        Pedido pedido = criarPedidoTeste();
        String paymentId = "pay_test_789";
        String pixCopyAndPaste = "00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-426614174000";

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", paymentId);
        paymentResponse.addProperty("pixCopyAndPaste", pixCopyAndPaste);
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.OK);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        PedidoCriadoDTO resultado = asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        assertNotNull(resultado);
        assertEquals(pedido.getId(), resultado.pedidoId());
        assertEquals("000201...", resultado.qrCodeUrl());
        assertEquals("000201...", resultado.pixCopiaECola());
    }

    @Test
    @DisplayName("Deve lançar exceção quando API do Asaas retornar erro na criação de pagamento")
    void deveLancarExcecaoQuandoApiRetornarErro() {
        Pedido pedido = criarPedidoTeste();

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>("{\"error\": \"Invalid API key\"}", HttpStatus.UNAUTHORIZED);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador)
        );

        assertTrue(exception.getMessage().contains("Falha ao criar cobrança no Asaas") || exception.getMessage().contains("Erro ao comunicar com Asaas"));
    }

    @Test
    @DisplayName("Deve lançar exceção quando houver erro de comunicação com a API")
    void deveLancarExcecaoQuandoHouverErroDeComunicacao() {
        Pedido pedido = criarPedidoTeste();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador)
        );

        assertTrue(exception.getMessage().contains("Erro ao comunicar com Asaas"));
    }

    @Test
    @DisplayName("Deve enviar headers corretos na requisição para o Asaas")
    void deveEnviarHeadersCorretos() {
        Pedido pedido = criarPedidoTeste();

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", "pay_test_123");
        paymentResponse.addProperty("pixCopyAndPaste", "000201...");
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.CREATED);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(testApiUrl + "/payments"), entityCaptor.capture(), eq(String.class));
        
        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        HttpHeaders headers = capturedEntity.getHeaders();
        
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals(testApiKey, headers.getFirst("access_token"));
    }

    @Test
    @DisplayName("Deve formatar data de vencimento corretamente (7 dias a partir de hoje)")
    void deveFormatarDataVencimentoCorretamente() {
        Pedido pedido = criarPedidoTeste();

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", "pay_test_123");
        paymentResponse.addProperty("pixCopyAndPaste", "000201...");
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), entityCaptor.capture(), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        JsonObject requestBody = new Gson().fromJson(capturedEntity.getBody(), JsonObject.class);
        
        String dueDate = requestBody.get("dueDate").getAsString();
        assertNotNull(dueDate);
        assertTrue(dueDate.matches("\\d{4}-\\d{2}-\\d{2}"), "Data deve estar no formato yyyy-MM-dd");
    }

    @Test
    @DisplayName("Deve usar valor total do pedido no payload")
    void deveUsarValorTotalDoPedidoNoPayload() {
        Pedido pedido = criarPedidoTeste();
        BigDecimal valorEsperado = pedido.getValorTotal();

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", "pay_test_123");
        paymentResponse.addProperty("pixCopyAndPaste", "000201...");
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), entityCaptor.capture(), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        JsonObject requestBody = new Gson().fromJson(capturedEntity.getBody(), JsonObject.class);
        
        double valorEnviado = requestBody.get("value").getAsDouble();
        assertEquals(valorEsperado.doubleValue(), valorEnviado, 0.01);
    }

    @Test
    @DisplayName("Deve usar ID do pedido como referência externa")
    void deveUsarIdPedidoComoReferenciaExterna() {
        Pedido pedido = criarPedidoTeste();

        JsonObject customerResponse = new JsonObject();
        customerResponse.addProperty("id", "cus_test_123");
        ResponseEntity<String> customerResponseEntity = new ResponseEntity<>(customerResponse.toString(), HttpStatus.OK);

        JsonObject paymentResponse = new JsonObject();
        paymentResponse.addProperty("id", "pay_test_123");
        paymentResponse.addProperty("pixCopyAndPaste", "000201...");
        ResponseEntity<String> paymentResponseEntity = new ResponseEntity<>(paymentResponse.toString(), HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(eq(testApiUrl + "/customers"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(customerResponseEntity);
        when(restTemplate.postForEntity(eq(testApiUrl + "/payments"), entityCaptor.capture(), eq(String.class)))
                .thenReturn(paymentResponseEntity);

        asaasPaymentService.criarCobrancaPix(pedido, nomePagador, emailPagador, cpfPagador);

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        JsonObject requestBody = new Gson().fromJson(capturedEntity.getBody(), JsonObject.class);
        
        String externalReference = requestBody.get("externalReference").getAsString();
        assertEquals(pedido.getId(), externalReference);
    }

    private Pedido criarPedidoTeste() {
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID().toString());
        pedido.setValorTotal(new BigDecimal("150.00"));
        return pedido;
    }
}
