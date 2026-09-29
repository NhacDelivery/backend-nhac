package br.com.nhac.backend_nhac.domain.pedido;

import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Concilia a cobrança externa antes de liberar o pedido e seu estoque. */
@Service
public class PagamentoExpiracaoService {
    private static final Logger log = LoggerFactory.getLogger(PagamentoExpiracaoService.class);
    private final PedidoRepository repository;
    private final PedidoService pedidoService;
    private final AsaasPaymentService asaas;
    private final StripePaymentService stripe;

    public PagamentoExpiracaoService(PedidoRepository repository, PedidoService pedidoService,
            AsaasPaymentService asaas, StripePaymentService stripe) {
        this.repository = repository;
        this.pedidoService = pedidoService;
        this.asaas = asaas;
        this.stripe = stripe;
    }

    @Scheduled(fixedDelay = 5000)
    public void expirarPendentes() {
        for (Pedido pedido : repository.findByStatusAndPagamentoExpiraEmLessThanEqual(StatusPedido.PENDENTE, Instant.now())) {
            try {
                reconciliar(pedido);
            } catch (Exception e) {
                // Sem confirmação do provedor, o pedido permanece ativo para outra tentativa.
                log.warn("Não foi possível conciliar o pagamento vencido do pedido {}", pedido.getId(), e);
            }
        }
    }

    private void reconciliar(Pedido pedido) {
        String forma = pedido.getFormaPagamento().toUpperCase(Locale.ROOT);
        if ("PIX".equals(forma) && pedido.getAsaasPaymentId() != null) {
            String status = asaas.consultarStatus(pedido.getAsaasPaymentId());
            if ("RECEIVED".equals(status) || "CONFIRMED".equals(status)) {
                pedidoService.marcarComoPagoPorAsaasPaymentId(pedido.getAsaasPaymentId());
                return;
            }
            if (!"DELETED".equals(status)) asaas.cancelarCobranca(pedido.getAsaasPaymentId());
        } else if (pedido.getStripePaymentIntentId() != null) {
            String status = stripe.consultarStatus(pedido.getStripePaymentIntentId());
            if ("succeeded".equals(status)) {
                pedidoService.marcarComoPagoPorPaymentIntentId(pedido.getStripePaymentIntentId());
                return;
            }
            if ("processing".equals(status)) return;
            if (!"canceled".equals(status)) stripe.cancelarPaymentIntent(pedido.getStripePaymentIntentId());
        } else if (!"PIX".equals(forma) && !"CARTAO".equals(forma)
                && !"STRIPE".equals(forma) && !"GOOGLE_PAY".equals(forma)) {
            return;
        }
        pedidoService.cancelarPorExpiracao(pedido.getId());
    }
}
