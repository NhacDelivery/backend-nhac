package br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvaliacaoEntregadorCreateDTO(
        @NotNull(message = "A nota é obrigatória")
        @Min(value = 1, message = "A nota mínima é 1")
        @Max(value = 5, message = "A nota máxima é 5")
        Integer nota,

        @Size(max = 500, message = "O comentário não pode ter mais que 500 caracteres")
        String comentario
) {}
