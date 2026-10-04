package br.com.nhac.backend_nhac.domain.entregador.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Agregado UTC por hora: no máximo 720 grupos no período de 30 dias. */
public record FreteHoraDTO(int ano, int mes, int dia, int hora, BigDecimal valor, long entregas) {
    public Instant referencia() {
        return LocalDateTime.of(ano, mes, dia, hora, 0).toInstant(ZoneOffset.UTC);
    }
}
