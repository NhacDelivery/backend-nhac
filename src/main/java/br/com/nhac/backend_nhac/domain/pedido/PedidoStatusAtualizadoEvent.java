package br.com.nhac.backend_nhac.domain.pedido;

public record PedidoStatusAtualizadoEvent(String pedidoId, StatusPedido status) {}
