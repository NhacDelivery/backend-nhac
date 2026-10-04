package br.com.nhac.backend_nhac.domain.feed;

import jakarta.validation.constraints.*;

public record FeedComentarioCreateDTO(@NotBlank @Size(max = 2000) String conteudo) {}
