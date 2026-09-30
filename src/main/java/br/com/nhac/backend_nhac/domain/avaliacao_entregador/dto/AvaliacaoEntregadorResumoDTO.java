package br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregador;
import java.time.Instant;

public record AvaliacaoEntregadorResumoDTO(
        String pedidoId,
        int nota,
        String comentario,
        String clienteNome,
        Instant criadoEm
) {
    public AvaliacaoEntregadorResumoDTO(AvaliacaoEntregador a) {
        this(
                a.getPedido().getId(),
                a.getNota(),
                a.getComentario(),
                extrairPrimeiroNome(a.getUsuario().getNome()),
                a.getCriadoEm()
        );
    }

    private static String extrairPrimeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "Cliente";
        String[] partes = nomeCompleto.trim().split("\\s+");
        return partes[0];
    }
}
