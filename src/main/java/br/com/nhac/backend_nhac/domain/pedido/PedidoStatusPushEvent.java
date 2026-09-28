package br.com.nhac.backend_nhac.domain.pedido;

public record PedidoStatusPushEvent(String pedidoId, String usuarioId, StatusPedido status) {}
