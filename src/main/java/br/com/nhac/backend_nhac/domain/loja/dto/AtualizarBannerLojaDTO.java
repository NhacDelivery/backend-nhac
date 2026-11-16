package br.com.nhac.backend_nhac.domain.loja.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record AtualizarBannerLojaDTO(
        @NotBlank @Size(max = 500) @URL(protocol = "https") String bannerUrl
) {}
