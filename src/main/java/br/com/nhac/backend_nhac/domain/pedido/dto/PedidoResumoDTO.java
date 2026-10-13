package br.com.nhac.backend_nhac.domain.pedido.dto;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Dados simplificados de um pedido para a listagem do histórico")
public record PedidoResumoDTO(
        @Schema(description = "ID do pedido") String id,
        @Schema(description = "ID da loja") String lojaId,
        @Schema(description = "Nome da loja") String lojaNome,
        @Schema(description = "Valor total do pedido") BigDecimal valorTotal,
        @Schema(description = "Status atual") StatusPedido status,
        @Schema(description = "Data de criação") Instant criadoEm,
        @Schema(description = "Forma de pagamento") String formaPagamento
) {
    public PedidoResumoDTO(String id, String lojaId, String lojaNome, BigDecimal valorTotal,
            StatusPedido status, Instant criadoEm) {
        this(id, lojaId, lojaNome, valorTotal, status, criadoEm, null);
    }
    public PedidoResumoDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getLoja() != null ? pedido.getLoja().getId() : null,
                pedido.getLoja() != null ? pedido.getLoja().getNome() : null,
                pedido.getValorTotal(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getFormaPagamento()
        );
    }
}
