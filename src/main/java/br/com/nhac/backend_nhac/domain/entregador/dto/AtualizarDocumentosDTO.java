package br.com.nhac.backend_nhac.domain.entregador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AtualizarDocumentosDTO(
        @jakarta.validation.constraints.Size(max = 30) String cnh,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos.") String cpf
) {}
