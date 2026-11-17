package br.com.nhac.backend_nhac.domain.entrega.dto;

import br.com.nhac.backend_nhac.domain.pedido.EnderecoEntrega;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;

import java.math.BigDecimal;

public record EntregaAtivaResponseDTO(
        String pedidoId,
        String lojaId,
        String lojaNome,
        String lojaEndereco,
        String lojaTelefone,
        Double lojaLatitude,
        Double lojaLongitude,
        String clienteNome,
        String clienteTelefone,
        EnderecoEntrega enderecoEntrega,
        Double entregaLatitude,
        Double entregaLongitude,
        BigDecimal valorTotal,
        BigDecimal taxaFrete,
        String formaPagamento,
        StatusPedido statusPedido,
        String observacao
) {
    public EntregaAtivaResponseDTO(Pedido pedido, String clienteNome, String clienteTelefone, String lojaTelefone) {
        this(
                pedido.getId(),
                pedido.getLoja() != null ? pedido.getLoja().getId() : null,
                pedido.getLoja() != null ? pedido.getLoja().getNome() : null,
                formatarEnderecoLoja(pedido),
                lojaTelefone,
                pedido.getLoja() != null && pedido.getLoja().getGeoLocalizacao() != null ? pedido.getLoja().getGeoLocalizacao().getGeoLat() : null,
                pedido.getLoja() != null && pedido.getLoja().getGeoLocalizacao() != null ? pedido.getLoja().getGeoLocalizacao().getGeoLng() : null,
                clienteNome,
                clienteTelefone,
                pedido.getEnderecoEntrega(),
                pedido.getEntregaLatitude(),
                pedido.getEntregaLongitude(),
                pedido.getValorTotal(),
                pedido.getTaxaFrete(),
                pedido.getFormaPagamento(),
                pedido.getStatus(),
                pedido.getObservacao()
        );
    }

    private static String formatarEnderecoLoja(Pedido pedido) {
        if (pedido.getLoja() == null || pedido.getLoja().getEndereco() == null) return "";
        var end = pedido.getLoja().getEndereco();
        return end.getRua() + ", " + end.getNumero() + " - " + end.getBairro() + ", " + end.getCidade();
    }
}
