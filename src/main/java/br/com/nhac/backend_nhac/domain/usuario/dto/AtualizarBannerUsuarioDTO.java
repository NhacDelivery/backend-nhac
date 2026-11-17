package br.com.nhac.backend_nhac.domain.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record AtualizarBannerUsuarioDTO(
        @NotBlank @Size(max = 500) @URL(protocol = "https") String bannerUrl
) {}
