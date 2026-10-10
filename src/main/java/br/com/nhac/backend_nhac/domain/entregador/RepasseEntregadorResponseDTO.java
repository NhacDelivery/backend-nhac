package br.com.nhac.backend_nhac.domain.entregador;

import java.math.BigDecimal;
import java.time.Instant;

public record RepasseEntregadorResponseDTO(
        String pedidoId, String entregadorId, BigDecimal valorDevido, BigDecimal valorPago,
        Instant apuradoEm, Instant pagoEm, String referenciaPagamento) {
    public static RepasseEntregadorResponseDTO de(RepasseEntregador repasse) {
        return new RepasseEntregadorResponseDTO(repasse.getPedidoId(), repasse.getEntregadorId(),
                repasse.getValorDevido(), repasse.getValorPago(), repasse.getApuradoEm(),
                repasse.getPagoEm(), repasse.getReferenciaPagamento());
    }
}
