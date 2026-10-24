package br.com.nhac.backend_nhac.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequestDTO(
        @Schema(description = "Campo legado ignorado; a identidade é gerada pelo servidor.", deprecated = true)
        @Size(max = 50) String id,
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank String telefone,
        @NotBlank
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).*$", message = "A senha deve conter pelo menos uma letra e um número.")
        String senha,
        @Schema(description = "Prova de uso único retornada por confirmar-email-cadastro.")
        @NotBlank(message = "Confirme seu e-mail antes de concluir o cadastro.")
        @Size(max = 100) String tokenCadastro
) {
}
