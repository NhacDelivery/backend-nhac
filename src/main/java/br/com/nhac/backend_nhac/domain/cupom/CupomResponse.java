package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CupomResponse(String id, String titulo, String descricao, String codigo,
        BigDecimal desconto, String tipo, BigDecimal usoMinimo, LocalDateTime dataValidade,
        String status, BigDecimal descontoAplicado) {
    public static CupomResponse from(Cupom c, BigDecimal aplicado) {
        return from(c, aplicado, c.getUsosAtuais());
    }
    public static CupomResponse from(Cupom c, BigDecimal aplicado, long usos) {
        String status = usos >= c.getLimiteUsos() ? "USADO"
                : !c.isAtivo() || (c.getDataValidade() != null && !c.getDataValidade().isAfter(LocalDateTime.now()))
                ? "EXPIRADO" : c.getInicio() != null && c.getInicio().isAfter(LocalDateTime.now()) ? "AGENDADO" : "DISPONIVEL";
        LocalDateTime validade = c.getDataValidade();
        if (c.getLoja() != null && validade != null) validade = validade.minusSeconds(1);
        return new CupomResponse(c.getId(), c.getLoja() == null ? "Cupom de boas-vindas" : c.getCodigo() + " · " + c.getLoja().getNome(), c.getDescricao(), c.getCodigo(),
                c.getValorDesconto(), c.getTipoDesconto(), c.getValorMinimoPedido(),
                validade, status, aplicado);
    }
}
