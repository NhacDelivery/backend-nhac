package br.com.nhac.backend_nhac.domain.pedido.dto;

import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import java.time.Instant;
import java.math.BigDecimal;

public record PagamentoPendenteDTO(
        String pedidoId,
        String formaPagamento,
        StatusPedido status,
        Instant expiraEm,
        BigDecimal valorTotal,
        String pixCopiaECola,
        String qrCodeUrl,
        String clientSecret,
        boolean simulacaoDisponivel
) {}
