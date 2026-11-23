package br.com.nhac.backend_nhac.domain.favorito.dto;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import java.time.Instant;

public record FavoritoResponseDTO(
        String id, String lojaId, String lojaNome, String lojaImagemUrl, Instant criadoEm,
        String categoria, String bannerUrl, Integer tempoEntregaMin, Integer tempoEntregaMax,
        Float avaliacaoMedia, Integer totalAvaliacoes
) {
    public static FavoritoResponseDTO de(String id, Loja loja, Instant criadoEm) {
        var dados = loja.getDadosOperacionais();
        return new FavoritoResponseDTO(id, loja.getId(), loja.getNome(), loja.getImagemUrl(), criadoEm,
                loja.getCategoria(), loja.getBannerUrl(), dados == null ? null : dados.getTempoEntregaMin(),
                dados == null ? null : dados.getTempoEntregaMax(), dados == null ? null : dados.getAvaliacaoMedia(),
                dados == null ? null : dados.getTotalAvaliacoes());
    }
}
