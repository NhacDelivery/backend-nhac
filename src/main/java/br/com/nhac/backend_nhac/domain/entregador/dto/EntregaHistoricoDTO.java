package br.com.nhac.backend_nhac.domain.entregador.dto;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma corrida no histórico do motoboy. Deliberadamente NÃO expõe os itens do
 * pedido nem o valor pago pelo cliente item a item: o que interessa ao
 * entregador é onde pegou, onde entregou, quando e quanto ele ganhou.
 */
public record EntregaHistoricoDTO(
        String pedidoId,
        String lojaNome,
        String bairroEntrega,
        String cidadeEntrega,
        BigDecimal taxaFrete,
        StatusPedido status,
        Instant coletadoEm,
        Instant entregueEm,
        Instant criadoEm
) {
    public EntregaHistoricoDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getLoja() != null ? pedido.getLoja().getNome() : null,
                pedido.getEnderecoEntrega() != null ? pedido.getEnderecoEntrega().getBairro() : null,
                pedido.getEnderecoEntrega() != null ? pedido.getEnderecoEntrega().getCidade() : null,
                pedido.getTaxaFrete(),
                pedido.getStatus(),
                pedido.getColetadoEm(),
                pedido.getEntregueEm(),
                pedido.getCriadoEm()
        );
    }
}
