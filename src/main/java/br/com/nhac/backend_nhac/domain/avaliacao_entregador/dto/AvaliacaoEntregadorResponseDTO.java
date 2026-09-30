package br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregador;
import java.time.Instant;

public record AvaliacaoEntregadorResponseDTO(
        String id,
        int nota,
        String comentario,
        Instant criadoEm
) {
    public AvaliacaoEntregadorResponseDTO(AvaliacaoEntregador a) {
        this(a.getId(), a.getNota(), a.getComentario(), a.getCriadoEm());
    }
}
