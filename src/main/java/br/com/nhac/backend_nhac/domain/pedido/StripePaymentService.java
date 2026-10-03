package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${nhac.payments.mock-mode:false}")
    private boolean mockMode;

    private final PedidoRepository pedidoRepository; // ✅ ADICIONADO

    public StripePaymentService(PedidoRepository pedidoRepository) { // ✅ INJEÇÃO ADICIONADA
        this.pedidoRepository = pedidoRepository;
    }

    @PostConstruct
    public void init() {
        if (!mockMode) {
            Stripe.apiKey = stripeApiKey;
            Stripe.setConnectTimeout(4000); Stripe.setReadTimeout(10000); Stripe.setMaxNetworkRetries(0);
        }
    }

    public PedidoCriadoDTO criarPaymentIntentCartao(Pedido pedido) {
        if (mockMode) {
            pedido.setStripePaymentIntentId("e2e_mock_cartao_" + pedido.getId());
            if (pedidoRepository.vincularStripe(pedido.getId(), pedido.getStripePaymentIntentId()) != 1)
                throw new IllegalStateException("Não foi possível vincular o pagamento ao pedido.");
            pedido.setPagamentoCriacaoIncerta(false);
            return new PedidoCriadoDTO(
                    pedido.getId(), "e2e_mock_client_secret", null, null);
        }
        if (pedido.getCriadoEm() != null && pedido.getCriadoEm().isBefore(java.time.Instant.now().minus(java.time.Duration.ofHours(23))))
            throw new IllegalStateException("Pagamento sem identificador requer conciliação manual após 23 horas.");
        try {
            // Stripe espera o valor em centavos (ex: R$ 50.00 -> 5000)
            long valorEmCentavos = pedido.getValorTotal().multiply(new BigDecimal("100")).longValue();

            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(valorEmCentavos)
                            .setCurrency("brl")
                            // Habilita métodos de pagamento automáticos (cartão e Google Pay)
                            .setAutomaticPaymentMethods(
                                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                            .setEnabled(true)
                                            .build()
                            )
                            .putMetadata("pedidoId", pedido.getId())
                            .putMetadata("usuarioId", pedido.getUsuarioId())
                            .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params, com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey("nhac-pedido-" + pedido.getId()).build());

            // Vincula o ID gerado pelo Stripe ao Pedido
            if (pedidoRepository.vincularStripe(pedido.getId(), paymentIntent.getId()) != 1)
                throw new IllegalStateException("Não foi possível vincular o pagamento ao pedido.");
            pedido.setStripePaymentIntentId(paymentIntent.getId());
            pedido.setPagamentoCriacaoIncerta(false);

            // Para cartão/Google Pay, não há QR Code PIX
            // Os campos pixCopiaECola e qrCodeUrl serão null
            String clientSecret = paymentIntent.getClientSecret();

            log.info("PaymentIntent criado: {}", paymentIntent.getId());
            return new PedidoCriadoDTO(pedido.getId(), clientSecret, null, null);

        } catch (StripeException e) {
            if (pedido.getStripePaymentIntentId() == null && e.getStatusCode() != null
                    && java.util.Set.of(400, 401, 402, 403, 404, 422).contains(e.getStatusCode())) {
                pedidoRepository.marcarCriacaoPagamento(pedido.getId(), false);
                pedido.setPagamentoCriacaoIncerta(false);
            }
            log.error("Erro ao criar PaymentIntent no Stripe", e);
            throw new RuntimeException("Falha ao comunicar com Stripe para criar PaymentIntent: " + e.getMessage(), e);
        }
    }

    public String obterClientSecret(Pedido pedido) {
        if (mockMode) return "e2e_mock_client_secret";
        try {
            return PaymentIntent.retrieve(pedido.getStripePaymentIntentId()).getClientSecret();
        } catch (StripeException e) {
            throw new IllegalStateException("Falha ao recuperar o pagamento com cartão.", e);
        }
    }

    public String consultarStatus(String paymentIntentId) {
        if (mockMode) return "requires_payment_method";
        try {
            return PaymentIntent.retrieve(paymentIntentId).getStatus();
        } catch (StripeException e) {
            throw new IllegalStateException("Falha ao consultar o pagamento com cartão.", e);
        }
    }

    public void cancelarPaymentIntent(String paymentIntentId) {
        if (mockMode) return;
        try {
            PaymentIntent.retrieve(paymentIntentId).cancel();
        } catch (StripeException e) {
            throw new IllegalStateException("Falha ao cancelar o pagamento com cartão.", e);
        }
    }
}
