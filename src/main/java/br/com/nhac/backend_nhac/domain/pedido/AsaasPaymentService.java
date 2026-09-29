package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class AsaasPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AsaasPaymentService.class);

    @Value("${asaas.api.key}")
    private String asaasApiKey;

    @Value("${asaas.api.url:https://sandbox.asaas.com/api/v3}")
    private String asaasApiUrl;

    @Value("${nhac.payments.mock-mode:false}")
    private boolean mockMode;

    private RestTemplate restTemplate;
    private Gson gson = new Gson();
    private final PedidoRepository pedidoRepository;

    public AsaasPaymentService(RestTemplate restTemplate, PedidoRepository pedidoRepository) {
        this.restTemplate = restTemplate;
        this.pedidoRepository = pedidoRepository;
    }

    private String obterOuCriarCustomer(String nome, String email, String cpfCnpj) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);

        JsonObject customerBody = new JsonObject();
        customerBody.addProperty("name", nome);
        customerBody.addProperty("email", email);
        customerBody.addProperty("cpfCnpj", cpfCnpj.replaceAll("\\D", ""));

        HttpEntity<String> entity = new HttpEntity<>(customerBody.toString(), headers);
        String url = asaasApiUrl + "/customers";

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
            JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
            return responseBody.get("id").getAsString();
        }

        throw new RuntimeException("Falha ao criar cliente no Asaas: " + response.getStatusCode());
    }

    /**
     * @param pedido 
     * @param nomePagador 
     * @param emailPagador 
     * @param cpfPagador 
     * @return 
     */
    public PedidoCriadoDTO criarCobrancaPix(Pedido pedido, String nomePagador, String emailPagador, String cpfPagador) {
        if (mockMode) {
            pedido.setAsaasPaymentId("e2e_mock_pix_" + pedido.getId());
            pedidoRepository.save(pedido);
            return new PedidoCriadoDTO(
                    pedido.getId(), null, "000201-e2e-mock", "e2e-mock-qr");
        }
        try {
            String customerId = obterOuCriarCustomer(nomePagador, emailPagador, cpfPagador);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", asaasApiKey);

            String dueDate = LocalDate.now().plusDays(7)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("customer", customerId);
            requestBody.addProperty("externalReference", pedido.getId());
            requestBody.addProperty("description", "Pedido #" + pedido.getId());
            requestBody.addProperty("billingType", "PIX");
            requestBody.addProperty("value", pedido.getValorTotal().doubleValue());
            requestBody.addProperty("dueDate", dueDate);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            String url = asaasApiUrl + "/payments";
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
                
                String paymentId = responseBody.get("id").getAsString();
                pedido.setAsaasPaymentId(paymentId);
                pedidoRepository.save(pedido); // ✅ SALVA O PEDIDO COM O ID DO ASAAS

                log.info("Cobrança PIX criada no Asaas: {}", paymentId);
                String codigoPix = obterCodigoPix(pedido);
                return new PedidoCriadoDTO(pedido.getId(), null, codigoPix, codigoPix);
            } else {
                throw new RuntimeException("Falha ao criar cobrança no Asaas: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Erro ao criar cobrança PIX no Asaas", e);
            throw new RuntimeException("Erro ao comunicar com Asaas para criar cobrança PIX: " + e.getMessage(), e);
        }
    }

    public String obterCodigoPix(Pedido pedido) {
        if (mockMode) return "000201-e2e-mock";
        JsonObject resposta = buscar("/payments/" + pedido.getAsaasPaymentId() + "/pixQrCode");
        if (!resposta.has("payload") || resposta.get("payload").isJsonNull()) {
            throw new IllegalStateException("O Asaas não devolveu o código PIX da cobrança.");
        }
        return resposta.get("payload").getAsString();
    }

    public String consultarStatus(String paymentId) {
        if (mockMode) return "PENDING";
        return buscar("/payments/" + paymentId).get("status").getAsString();
    }

    public void cancelarCobranca(String paymentId) {
        if (mockMode) return;
        restTemplate.exchange(asaasApiUrl + "/payments/" + paymentId,
                HttpMethod.DELETE, new HttpEntity<>(cabecalhos()), String.class);
    }

    private JsonObject buscar(String path) {
        ResponseEntity<String> resposta = restTemplate.exchange(asaasApiUrl + path,
                HttpMethod.GET, new HttpEntity<>(cabecalhos()), String.class);
        if (!resposta.getStatusCode().is2xxSuccessful() || resposta.getBody() == null) {
            throw new IllegalStateException("Falha ao consultar cobrança PIX no Asaas.");
        }
        return gson.fromJson(resposta.getBody(), JsonObject.class);
    }

    private HttpHeaders cabecalhos() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", asaasApiKey);
        return headers;
    }
}
