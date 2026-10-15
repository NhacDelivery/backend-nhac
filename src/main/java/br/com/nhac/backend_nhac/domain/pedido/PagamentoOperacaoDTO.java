package br.com.nhac.backend_nhac.domain.pedido;

import java.time.Instant;

/** Não expõe tokens, documentos nem identificadores externos ao cliente. */
public record PagamentoOperacaoDTO(String pedidoId, String estado, Instant atualizadoEm, boolean podeSolicitarEstorno) {
    public static PagamentoOperacaoDTO de(Pedido p) {
        String estado = p.getEstornoStatus() == null ?
                (p.getStatus() == StatusPedido.PENDENTE && (p.isPagamentoCriacaoIncerta()
                    || p.getPagamentoExpiraEm() != null && !p.getPagamentoExpiraEm().isAfter(Instant.now()))
                        ? "CONCILIACAO" : "REGULAR") : "ESTORNO_" + p.getEstornoStatus().name();
        return new PagamentoOperacaoDTO(p.getId(), estado, p.getEstornoAtualizadoEm(),
                p.getStatus() == StatusPedido.PAGO && p.getEstornoStatus() == null && !p.isPagamentoCriacaoIncerta()
                && switch (p.getFormaPagamento() == null ? "" : p.getFormaPagamento().toUpperCase(java.util.Locale.ROOT)) {
                    case "PIX" -> p.getAsaasPaymentId() != null && p.getStripePaymentIntentId() == null;
                    case "CARTAO", "STRIPE", "GOOGLE_PAY" -> p.getStripePaymentIntentId() != null && p.getAsaasPaymentId() == null;
                    default -> false;
                });
    }
}
