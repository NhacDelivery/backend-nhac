package br.com.nhac.backend_nhac.domain.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UsuarioAtualizarDTO(
        String nome,
        @Email
        String email,
        @Size(max = 20)
        String telefone,
        String imagemUrl,
        @Size(max = 255) String fcmToken,
        String cpf
) {
}