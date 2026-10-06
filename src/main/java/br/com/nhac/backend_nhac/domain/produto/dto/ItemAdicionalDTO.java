package br.com.nhac.backend_nhac.domain.produto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * DTO para item individual de um grupo de adicionais.
 */
public record ItemAdicionalDTO(

        @Schema(description = "Nome do item adicional", example = "Molho Barbecue")
        @NotBlank(message = "O nome do item adicional não pode estar vazio.")
        String nome,

        @Schema(description = "Preço adicional deste item", example = "2.50")
        @NotNull(message = "O preço do item adicional é obrigatório.")
        @PositiveOrZero(message = "O preço não pode ser negativo.")
        BigDecimal preco,
        String id
) {
    public ItemAdicionalDTO(String nome, BigDecimal preco) { this(nome, preco, null); }
}
