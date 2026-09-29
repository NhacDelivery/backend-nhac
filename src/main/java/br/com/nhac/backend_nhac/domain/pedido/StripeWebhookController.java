package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.PedidoService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@Tag(name = "Stripe Webhooks", description = "Endpoints para recebimento de eventos assíncronos de pagamento do Stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PedidoService pedidoService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    public StripeWebhookController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @Operation(summary = "Receber webhooks do Stripe", description = "Rota aberta chamada pelo Stripe para atualizar o status do pedido")
    @PostMapping
    public ResponseEntity<String> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null) {
            log.warn("Webhook Stripe sem Stripe-Signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Faltando header Stripe-Signature");
        }

        Event event = null;

        try {
            // Verifica a assinatura e desserializa o evento usando a SDK
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            // Assinatura inválida (pode ser alguém tentando invadir a API)
            log.warn("Webhook Stripe com assinatura inválida: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.warn("Payload inválido recebido no webhook Stripe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        }

        // Trata os tipos de eventos suportados
        switch (event.getType()) {
            case "payment_intent.succeeded":
                PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (paymentIntent != null) {
                    log.info("Webhook Stripe confirmou pagamento do PaymentIntent {}", paymentIntent.getId());
                    try {
                        pedidoService.marcarComoPagoPorPaymentIntentId(paymentIntent.getId());
                    } catch (br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException e) {
                        log.info("Webhook Stripe idempotente/ignorado: {}", e.getMessage());
                    } catch (Exception e) {
                        log.error("Erro ao processar confirmação Stripe", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // Força o retry do Stripe
                    }
                }
                break;
            case "payment_intent.payment_failed":
                // Uma tentativa falha pode ser repetida até o vencimento do pedido.
                log.info("Tentativa Stripe falhou; pagamento ainda pode ser retomado.");
                break;
            default:
                log.debug("Evento Stripe não tratado: {}", event.getType());
        }

        return ResponseEntity.ok("Success");
    }
}
