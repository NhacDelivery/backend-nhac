package br.com.nhac.backend_nhac.domain.loja.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record AtualizarLocalizacaoLojaDTO(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Valid LojaCreateDTO.EnderecoDTO endereco
) {
    // Clientes existentes podem continuar atualizando somente as coordenadas.
    public AtualizarLocalizacaoLojaDTO(Double latitude, Double longitude) {
        this(latitude, longitude, null);
    }
}
