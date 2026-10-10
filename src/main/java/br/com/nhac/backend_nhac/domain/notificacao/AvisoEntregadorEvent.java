package br.com.nhac.backend_nhac.domain.notificacao;
public record AvisoEntregadorEvent(String id, String usuarioId, String tipo, String texto,
        String pedidoId, String lojaId, String lojaNome, String ofertaId) {}
