package br.com.nhac.backend_nhac.domain.feed;

import jakarta.validation.constraints.*;
import java.util.List;

public record FeedPostCreateDTO(
        @NotBlank @Size(max = 5000) String conteudo,
        @Size(max = 6) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+", message = "Use uma URL HTTPS") String> imagens,
        @Size(max = 10) List<@NotBlank @Size(max = 60) @Pattern(regexp = "#[\\p{L}\\p{N}_]+", message = "Hashtag inválida") String> hashTags,
        @Size(max = 50) String lojaId,
        @com.fasterxml.jackson.annotation.JsonProperty("isPatrocinado") Boolean isPatrocinado,
        @Size(max = 100) String sponsorLabel) {
    public FeedPostCreateDTO {
        if (isPatrocinado == null) isPatrocinado = false;
    }
}
