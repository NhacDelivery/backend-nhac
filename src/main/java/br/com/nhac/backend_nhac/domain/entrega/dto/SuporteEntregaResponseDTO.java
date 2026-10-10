package br.com.nhac.backend_nhac.domain.entrega.dto;

import br.com.nhac.backend_nhac.domain.entrega.SolicitacaoSuporte;
import java.time.Instant;

public record SuporteEntregaResponseDTO(
    String id,
    String pedidoId,
    String usuarioId,
    String motivo,
    String descricao,
    String etapa,
    String status,
    String resposta,
    Instant criadoEm,
    Instant respondidoEm,
    String acao,
    String responsavelUsuarioId) {
  public static SuporteEntregaResponseDTO de(SolicitacaoSuporte ticket) {
    return new SuporteEntregaResponseDTO(
        ticket.getId(),
        ticket.getPedidoId(),
        ticket.getUsuarioId(),
        ticket.getMotivo(),
        ticket.getDescricao(),
        ticket.getEtapa(),
        ticket.getStatus(),
        ticket.getResposta(),
        ticket.getCriadoEm(),
        ticket.getRespondidoEm(),
        ticket.getAcao(),
        ticket.getResponsavelUsuarioId());
  }
}
