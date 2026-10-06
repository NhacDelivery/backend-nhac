package br.com.nhac.backend_nhac.domain.feed;
import jakarta.validation.constraints.*;
public record FeedComentarioCreateDTO(@NotBlank @Size(max = 2000) String conteudo,
        @Size(max = 50) String respostaAId) {
    public FeedComentarioCreateDTO(String conteudo) { this(conteudo, null); }
}
