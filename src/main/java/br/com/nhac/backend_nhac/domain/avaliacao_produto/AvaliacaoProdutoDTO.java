package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import jakarta.validation.constraints.*;
import java.util.List;
public record AvaliacaoProdutoDTO(@NotBlank String pedidoId, @NotNull @Min(1) @Max(5) Integer nota,
    @Size(max=500) String comentario,
    @Size(max=6) List<@NotBlank @Size(max=2048) @Pattern(regexp="https://[^\\s]+") String> imagens) {}
