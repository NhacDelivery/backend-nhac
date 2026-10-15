package br.com.nhac.backend_nhac.domain.pedido;

import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Fila persistida. O CAS antes do POST impede duas instâncias de enviarem o mesmo estorno. */
@Service
public class PagamentoEstornoService {
    private final PedidoRepository repository;
    private final AsaasPaymentService asaas;
    private final StripePaymentService stripe;
    private final PagamentoMetricas metricas;
    public PagamentoEstornoService(PedidoRepository repository, AsaasPaymentService asaas,
            StripePaymentService stripe, PagamentoMetricas metricas) {
        this.repository = repository; this.asaas = asaas; this.stripe = stripe; this.metricas = metricas;
    }
    @Scheduled(fixedDelayString = "${nhac.payments.estorno-delay-ms:60000}")
    public void processarFila() {
        for (Pedido pedido : repository.findTop50ByEstornoStatusInAndEstornoAtualizadoEmBeforeOrderByEstornoAtualizadoEmAsc(
                List.of(StatusEstorno.SOLICITADO, StatusEstorno.ENVIANDO, StatusEstorno.PROCESSANDO, StatusEstorno.REVISAO),
                Instant.now().minusSeconds(60))) {
            processar(pedido);
        }
    }
    void processar(Pedido pedido) {
        StatusEstorno anterior = pedido.getEstornoStatus();
        boolean enviar = anterior == StatusEstorno.SOLICITADO;
        if (enviar && repository.transicionarEstorno(pedido.getId(), anterior, StatusEstorno.ENVIANDO, Instant.now()) != 1) return;
        StatusEstorno esperado = enviar ? StatusEstorno.ENVIANDO : anterior;
        String etapa = enviar ? "estorno_enviar" : "estorno_consultar";
        try {
            StatusEstorno resultado = pedido.getAsaasPaymentId() != null
                    ? (enviar ? asaas.estornar(pedido) : asaas.consultarEstorno(pedido))
                    : (enviar ? stripe.estornar(pedido) : stripe.consultarEstorno(pedido));
            if (resultado == null) throw new IllegalStateException("Resposta de estorno ausente.");
            repository.transicionarEstorno(pedido.getId(), esperado, resultado, Instant.now());
            metricas.registrar(etapa, resultado == StatusEstorno.CONCLUIDO ? "concluido" : "pendente");
        } catch (Exception e) {
            // Timeout ou rejeição: nunca repetir POST. Consultar o provedor ou revisar no painel.
            repository.transicionarEstorno(pedido.getId(), esperado, StatusEstorno.REVISAO, Instant.now());
            metricas.registrar(etapa, "falha");
        }
    }
}
