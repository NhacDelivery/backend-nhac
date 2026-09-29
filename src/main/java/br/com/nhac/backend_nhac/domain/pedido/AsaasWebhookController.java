package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.PedidoService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class AsaasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

    private final PedidoService pedidoService;
    private final String asaasWebhookToken;

    public AsaasWebhookController(PedidoService pedidoService,
                                  @Value("${asaas.webhook.token}") String asaasWebhookToken) {
        this.pedidoService = pedidoService;
        this.asaasWebhookToken = asaasWebhookToken;
    }

    /**
     * Endpoint para receber webhooks do Asaas
     * 
     * Eventos suportados:
     * - PAYMENT_RECEIVED: Pagamento confirmado → Marca pedido como PAGO
     * - PAYMENT_OVERDUE: Pagamento vencido → Cancela pedido
     * - PAYMENT_CANCELLED: Pagamento cancelado → Cancela pedido
     */
    @PostMapping("/asaas")
    public ResponseEntity<Void> receberWebhookAsaas(
            @RequestBody String payloadJson,
            @RequestHeader(value = "asaas-access-token", required = false) String receivedToken,
            HttpServletRequest request) {

        if (receivedToken == null || !asaasWebhookToken.equals(receivedToken)) {
            log.warn("Webhook Asaas com token inválido");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonObject payload = JsonParser.parseString(payloadJson).getAsJsonObject();
            
            String notification = payload.has("notification") ? payload.get("notification").getAsString() : null;
            
            if (notification == null && payload.has("event")) {
                notification = payload.get("event").getAsString();
            }

            if (notification == null) {
                log.warn("Webhook Asaas sem campo notification/event");
                return ResponseEntity.badRequest().build();
            }

            JsonObject paymentData = payload.has("payment") ? payload.getAsJsonObject("payment") : payload;
            String externalReference = paymentData.has("externalReference") 
                    ? paymentData.get("externalReference").getAsString() 
                    : null;
            String asaasPaymentId = paymentData.has("id") 
                    ? paymentData.get("id").getAsString() 
                    : null;

            String pedidoId = extrairPedidoIdDaReferencia(externalReference);

            log.info("Webhook Asaas recebido: evento={}, paymentId={}", notification, asaasPaymentId);

            switch (notification) {
                case "PAYMENT_RECEIVED":
                case "PAYMENT_CONFIRMED":
                    if (asaasPaymentId != null && !asaasPaymentId.isEmpty()) {
                        log.info("Webhook Asaas confirmou pagamento {}", asaasPaymentId);
                        try {
                            pedidoService.marcarComoPagoPorAsaasPaymentId(asaasPaymentId);
                        } catch (br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException e) {
                            log.info("Webhook Asaas idempotente/ignorado: {}", e.getMessage());
                        }
                    } else {
                        log.warn("Webhook Asaas sem payment id");
                        return ResponseEntity.badRequest().build();
                    }
                    break;

                case "PAYMENT_OVERDUE":
                    if (pedidoId != null && !pedidoId.isEmpty()) {
                        log.warn("Webhook Asaas informou pagamento vencido para pedido {}", pedidoId);
                        try {
                            pedidoService.cancelarPorFalhaPagamentoAsaas(pedidoId);
                        } catch (br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException e) {
                            log.info("Webhook Asaas idempotente/ignorado: {}", e.getMessage());
                        }
                    } else {
                        log.warn("Webhook Asaas PAYMENT_OVERDUE sem pedidoId");
                    }
                    break;

                case "PAYMENT_CANCELLED":
                    if (pedidoId != null && !pedidoId.isEmpty()) {
                        log.warn("Webhook Asaas informou pagamento cancelado para pedido {}", pedidoId);
                        try {
                            pedidoService.cancelarPorFalhaPagamentoAsaas(pedidoId);
                        } catch (br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException e) {
                            log.info("Webhook Asaas idempotente/ignorado: {}", e.getMessage());
                        }
                    } else {
                        log.warn("Webhook Asaas PAYMENT_CANCELLED sem pedidoId");
                    }
                    break;

                default:
                    log.debug("Evento Asaas não tratado: {}", notification);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Erro ao processar webhook Asaas", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    
    private String extrairPedidoIdDaReferencia(String externalReference) {
        if (externalReference == null || externalReference.isEmpty()) {
            return null;
        }

        if (externalReference.startsWith("pedidoId_")) {
            return externalReference.substring("pedidoId_".length());
        }

        return externalReference;
    }
}
