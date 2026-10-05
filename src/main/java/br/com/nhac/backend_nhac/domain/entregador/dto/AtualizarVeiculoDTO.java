package br.com.nhac.backend_nhac.domain.entregador.dto;

import br.com.nhac.backend_nhac.domain.entregador.TipoVeiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizarVeiculoDTO(
        @NotNull TipoVeiculo tipoVeiculo,
        @Size(max = 20) String placaVeiculo,
        @Size(max = 60) String modeloVeiculo,
        @Size(max = 30) String corVeiculo
) {}
