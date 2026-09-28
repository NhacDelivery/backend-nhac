package br.com.nhac.backend_nhac.domain.produto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para grupo de adicionais de um produto.
 */
public record GrupoAdicionalDTO(

        @Schema(description = "Nome do grupo de adicionais", example = "Escolha o molho")
        @NotBlank(message = "O nome do grupo de adicionais não pode estar vazio.")
        String nome,

        @Schema(description = "Indica se o grupo é obrigatório", example = "false")
        boolean obrigatorio,

        @Schema(description = "Quantidade mínima de itens a selecionar", example = "1")
        Integer minimo,

        @Schema(description = "Quantidade máxima de itens a selecionar", example = "2")
        Integer maximo,

        @Schema(description = "Lista de itens deste grupo de adicionais")
        @NotNull(message = "A lista de itens não pode ser nula.")
        List<@jakarta.validation.Valid ItemAdicionalDTO> itens
) {
}
