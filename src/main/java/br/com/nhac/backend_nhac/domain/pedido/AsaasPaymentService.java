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
    private final PagamentoClienteRepository clienteRepository;

    public AsaasPaymentService(RestTemplate restTemplate, PedidoRepository pedidoRepository, PagamentoClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
        this.restTemplate = restTemplate;
        this.pedidoRepository = pedidoRepository;
    }

    private String obterOuCriarCustomer(String usuarioId, String nome, String email, String cpfCnpj) {
        String raw = asaasApiUrl + ":" + usuarioId + ":" + cpfCnpj.replaceAll("\\D", "");
        String chave;
        try {
            chave = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var salvo = clienteRepository.findById(chave);
        if (salvo.isPresent()) return resolverCliente(chave, salvo.get());
        try {
            // A PK faz o claim entre JVMs. Nenhuma transação fica aberta durante HTTP.
            clienteRepository.reservarCriacao(chave);
        } catch (org.springframework.dao.DataIntegrityViolationException concorrente) {
            return resolverCliente(chave, clienteRepository.findById(chave).orElseThrow());
        }
        try {
            String customerId = criarCustomer(nome, email, cpfCnpj, chave);
            clienteRepository.concluirCriacao(chave, customerId);
            return customerId;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (java.util.Set.of(400, 401, 403, 422).contains(e.getStatusCode().value()))
                clienteRepository.liberarRejeicao(chave);
            throw e;
        }
    }

    private String resolverCliente(String chave, PagamentoCliente cliente) {
        if (!"__CRIACAO_PENDENTE__".equals(cliente.getCustomerId())) return cliente.getCustomerId();
        var data = buscar("/customers?externalReference=" + chave).getAsJsonArray("data");
        if (data == null || data.size() != 1)
            throw new IllegalStateException("Cadastro do pagador em conciliação. Não repetir criação de resultado incerto.");
        String id = data.get(0).getAsJsonObject().get("id").getAsString();
        clienteRepository.concluirCriacao(chave, id);
        return id;
    }

    private String criarCustomer(String nome, String email, String cpfCnpj, String referencia) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);

        JsonObject customerBody = new JsonObject();
        customerBody.addProperty("name", nome);
        customerBody.addProperty("email", email);
        customerBody.addProperty("cpfCnpj", cpfCnpj.replaceAll("\\D", ""));
        customerBody.addProperty("externalReference", referencia);

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
            if (pedidoRepository.vincularAsaas(pedido.getId(), pedido.getAsaasPaymentId()) != 1)
                throw new IllegalStateException("Não foi possível vincular a cobrança ao pedido.");
            return new PedidoCriadoDTO(
                    pedido.getId(), null, "000201-e2e-mock", "e2e-mock-qr");
        }
        boolean pagamentoEnviado = false;
        try {
            String customerId = obterOuCriarCustomer(pedido.getUsuarioId(), nomePagador, emailPagador, cpfPagador);

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
            pedidoRepository.marcarCriacaoPagamento(pedido.getId(), true);
            pedido.setPagamentoCriacaoIncerta(true);
            pagamentoEnviado = true;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
                
                String paymentId = responseBody.get("id").getAsString();
                if (pedidoRepository.vincularAsaas(pedido.getId(), paymentId) != 1)
                    throw new IllegalStateException("Não foi possível vincular a cobrança ao pedido.");
                pedido.setAsaasPaymentId(paymentId);
                pedido.setPagamentoCriacaoIncerta(false);

                log.info("Cobrança PIX criada no Asaas: {}", paymentId);
                String codigoPix = obterCodigoPix(pedido);
                return new PedidoCriadoDTO(pedido.getId(), null, codigoPix, codigoPix);
            } else {
                throw new RuntimeException("Falha ao criar cobrança no Asaas: " + response.getStatusCode());
            }

        } catch (Exception e) {
            boolean rejeicaoDefinitiva = e instanceof org.springframework.web.client.HttpClientErrorException erro
                    && erro.getStatusCode().value() != 408;
            if (pedido.getAsaasPaymentId() == null && (!pagamentoEnviado || rejeicaoDefinitiva)) {
                pedidoRepository.marcarCriacaoPagamento(pedido.getId(), false);
                pedido.setPagamentoCriacaoIncerta(false);
            }
            log.error("Erro ao criar cobrança PIX no Asaas", e);
            throw new RuntimeException("Erro ao comunicar com Asaas para criar cobrança PIX: " + e.getMessage(), e);
        }
    }

    public boolean recuperarCobranca(Pedido pedido) {
        if (pedido.getAsaasPaymentId() != null) return true;
        if (mockMode) return false;
        JsonObject resposta = buscar("/payments?externalReference=" +
                java.net.URLEncoder.encode(pedido.getId(), java.nio.charset.StandardCharsets.UTF_8));
        var data = resposta.getAsJsonArray("data");
        if (data == null || data.isEmpty()) return false;
        if (data.size() != 1) throw new IllegalStateException("Cobranças múltiplas exigem conciliação manual.");
        JsonObject payment = data.get(0).getAsJsonObject();
        if (!pedido.getId().equals(payment.get("externalReference").getAsString()))
            throw new IllegalStateException("Referência de pagamento divergente.");
        String id = payment.get("id").getAsString();
        if (pedidoRepository.vincularAsaas(pedido.getId(), id) != 1)
            throw new IllegalStateException("Não foi possível vincular a cobrança recuperada ao pedido.");
        pedido.setAsaasPaymentId(id); pedido.setPagamentoCriacaoIncerta(false);
        return true;
    }
    public String obterCodigoPix(Pedido pedido) {
        if (mockMode) return "000201-e2e-mock";
        JsonObject resposta = buscar("/payments/" + pedido.getAsaasPaymentId() + "/pixQrCode");
        if (resposta == null || !resposta.has("payload") || resposta.get("payload").isJsonNull()
                || !resposta.get("payload").isJsonPrimitive() || !resposta.get("payload").getAsJsonPrimitive().isString()
                || resposta.get("payload").getAsString().isBlank()) {
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

    public StatusEstorno estornar(Pedido pedido) {
        if (mockMode) return StatusEstorno.CONCLUIDO;
        // Verifica estorno prévio/manual antes de enviar qualquer débito.
        String status = consultarStatus(pedido.getAsaasPaymentId());
        if ("REFUNDED".equals(status)) return StatusEstorno.CONCLUIDO;
        if (!"RECEIVED".equals(status) && !"CONFIRMED".equals(status)) return StatusEstorno.REVISAO;
        HttpHeaders headers = cabecalhos(); headers.setContentType(MediaType.APPLICATION_JSON);
        JsonObject body = new JsonObject();
        body.addProperty("value", pedido.getValorTotal());
        body.addProperty("description", "Cancelamento do pedido " + pedido.getId());
        restTemplate.postForEntity(asaasApiUrl + "/payments/" + pedido.getAsaasPaymentId() + "/refund",
                new HttpEntity<>(body.toString(), headers), String.class);
        return consultarEstorno(pedido);
    }

    public StatusEstorno consultarEstorno(Pedido pedido) {
        if (mockMode) return StatusEstorno.CONCLUIDO;
        return switch (consultarStatus(pedido.getAsaasPaymentId())) {
            case "REFUNDED" -> StatusEstorno.CONCLUIDO;
            case "REFUND_REQUESTED", "REFUND_IN_PROGRESS" -> StatusEstorno.PROCESSANDO;
            default -> StatusEstorno.REVISAO;
        };
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
