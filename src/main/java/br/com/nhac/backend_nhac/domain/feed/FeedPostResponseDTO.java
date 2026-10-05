package br.com.nhac.backend_nhac.domain.feed;

import java.time.Instant;
import java.util.List;

public record FeedPostResponseDTO(
        String id, String usuarioId, String nomeUsuario, String avatarUrl,
        String conteudo, List<String> imagens, List<String> hashTags,
        long curtidas, long comentarios, long salvos, boolean curtido, boolean salvo,
        @com.fasterxml.jackson.annotation.JsonProperty("isPatrocinado") boolean isPatrocinado, String sponsorLabel, MentionedStoreDTO mentionedStore,
        Instant criadoEm, Instant atualizadoEm, FeedComentarioResponseDTO topComment, boolean podeEditar) {
    public record MentionedStoreDTO(String id, String nome, String imageUrl, float rating, String avaliacoes) {}
}
