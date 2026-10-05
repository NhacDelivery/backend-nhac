package br.com.nhac.backend_nhac.domain.feed;

import java.time.Instant;

public record FeedComentarioResponseDTO(String id, String usuarioId, String nomeUsuario,
        String avatarUrl, String conteudo, @com.fasterxml.jackson.annotation.JsonProperty("isAuthor") boolean isAuthor, Instant criadoEm, boolean podeExcluir) {}
