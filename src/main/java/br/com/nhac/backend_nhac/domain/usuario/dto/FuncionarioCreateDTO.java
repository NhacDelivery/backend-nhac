package br.com.nhac.backend_nhac.domain.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de funcionário pelo lojista. Decisão do produto: o próprio lojista
 * define a senha inicial aqui (sem fluxo de convite por e-mail). "cargo" é só
 * um cargo do painel (Administrador/Gerente/Atendente), avaliado por
 * LojaAccessService. Gestão da equipe continua restrita ao dono.
 */
public record FuncionarioCreateDTO(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank String telefone,
        @NotBlank
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).*$", message = "A senha deve conter pelo menos uma letra e um número.")
        String senha,
        @NotBlank String cargo
) {
}
