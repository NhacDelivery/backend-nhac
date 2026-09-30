package br.com.nhac.backend_nhac.domain.entrega.dto;

import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record EntregadorPedidoDTO(
        String nome,
        String fotoUrl,
        String tipoVeiculo,
        String modeloVeiculo,
        String corVeiculo,
        String placaVeiculo,
        Double avaliacaoMedia,
        Long totalAvaliacoes
) {
    public static EntregadorPedidoDTO from(Entregador entregador, Double media, Long total) {
        if (entregador == null) return null;
        Usuario u = entregador.getUsuario();
        
        Double mediaArredondada = null;
        if (media != null) {
            mediaArredondada = BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        return new EntregadorPedidoDTO(
                formatarNomeSeguro(u.getNome()),
                u.getImagemUrl(),
                entregador.getTipoVeiculo() != null ? entregador.getTipoVeiculo().name() : null,
                entregador.getModeloVeiculo(),
                entregador.getCorVeiculo(),
                entregador.getPlacaVeiculo(),
                mediaArredondada,
                total
        );
    }

    private static String formatarNomeSeguro(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "Entregador";
        String[] partes = nomeCompleto.trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        return partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
    }
}
