package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import java.math.BigDecimal;
import java.time.Instant;

public record ExtratoRepasseDTO(
    String pedidoId,
    BigDecimal freteCalculado,
    BigDecimal valorDevido,
    BigDecimal valorPago,
    String status,
    Instant apuradoEm,
    Instant pagoEm,
    String referencia) {
  public static ExtratoRepasseDTO de(Pedido p, RepasseEntregador r) {
    return new ExtratoRepasseDTO(
        p.getId(),
        p.getTaxaFrete(),
        r == null ? null : r.getValorDevido(),
        r == null ? null : r.getValorPago(),
        r == null ? "NAO_APURADO" : r.getPagoEm() == null ? "PENDENTE" : "PAGO",
        r == null ? null : r.getApuradoEm(),
        r == null ? null : r.getPagoEm(),
        r == null ? null : r.getReferenciaPagamento());
  }
}
