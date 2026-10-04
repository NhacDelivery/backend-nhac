package br.com.nhac.backend_nhac.domain.entregador.dto;

import br.com.nhac.backend_nhac.domain.entrega.dto.EntregaAtivaResponseDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.OfertaEntregaDTO;
import java.time.Instant;
import java.util.List;

public record EstadoEntregadorDTO(EntregadorResponseDTO perfil, EntregaAtivaResponseDTO entrega,
        List<OfertaEntregaDTO> ofertas, Instant atualizadoEm) {}
