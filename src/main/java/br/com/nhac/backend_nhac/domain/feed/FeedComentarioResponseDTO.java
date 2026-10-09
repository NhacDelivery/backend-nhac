package br.com.nhac.backend_nhac.domain.feed;
import java.time.Instant;
public record FeedComentarioResponseDTO(String id, String usuarioId, String nomeUsuario,
        String avatarUrl, String conteudo, @com.fasterxml.jackson.annotation.JsonProperty("isAuthor") boolean isAuthor,
        Instant criadoEm, boolean podeExcluir, int curtidas, boolean curtido, String respostaAId, String respostaANome) {
    public FeedComentarioResponseDTO(String id, String usuarioId, String nomeUsuario, String avatarUrl,
            String conteudo, boolean isAuthor, Instant criadoEm, boolean podeExcluir) {
        this(id, usuarioId, nomeUsuario, avatarUrl, conteudo, isAuthor, criadoEm, podeExcluir, 0, false, null, null);
    }
}
