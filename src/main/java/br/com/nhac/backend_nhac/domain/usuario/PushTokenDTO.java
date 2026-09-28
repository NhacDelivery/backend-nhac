package br.com.nhac.backend_nhac.domain.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenDTO(@NotBlank @Size(max = 2048) String token) {}
