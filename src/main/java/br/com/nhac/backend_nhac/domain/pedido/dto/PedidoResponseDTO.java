package br.com.nhac.backend_nhac.domain.pedido.dto;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Dados detalhados de um pedido")
public record PedidoResponseDTO(
        @Schema(description = "ID do pedido") String id,
        @Schema(description = "ID do usuário que fez o pedido") String usuarioId,
        @Schema(description = "ID da loja") String lojaId,
        @Schema(description = "Nome da loja") String lojaNome,
        @Schema(description = "Valor total do pedido") BigDecimal valorTotal,
        @Schema(description = "Taxa de frete cobrada") BigDecimal taxaFrete,
        @Schema(description = "Forma de pagamento") String formaPagamento,
        @Schema(description = "Troco para") BigDecimal trocoPara,
        @Schema(description = "Observações") String observacao,
        @Schema(description = "Status atual do pedido") StatusPedido status,
        @Schema(description = "Data e hora da criação") Instant criadoEm,
        @Schema(description = "Endereço onde será entregue") EnderecoEntregaResponseDTO enderecoEntrega,
        @Schema(description = "Itens do pedido") List<ItemPedidoResponseDTO> itens,
        BigDecimal desconto,
        String cupomId,
        String codigoEntrega,
        br.com.nhac.backend_nhac.domain.entrega.dto.EntregadorPedidoDTO entregador,
        Boolean entregadorAvaliado
) {
    public PedidoResponseDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getLoja().getId(),
                pedido.getLoja().getNome(),
                pedido.getValorTotal(),
                pedido.getTaxaFrete(),
                pedido.getFormaPagamento(),
                pedido.getTrocoPara(),
                pedido.getObservacao(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getEnderecoEntrega() != null ? new EnderecoEntregaResponseDTO(
                        pedido.getEnderecoEntrega().getRua(),
                        pedido.getEnderecoEntrega().getNumero(),
                        pedido.getEnderecoEntrega().getBairro(),
                        pedido.getEnderecoEntrega().getCidade(),
                        pedido.getEnderecoEntrega().getEstado(),
                        pedido.getEnderecoEntrega().getCep(),
                        pedido.getEnderecoEntrega().getComplemento()
                ) : null,
                pedido.getItens() != null ? pedido.getItens().stream().map(item -> new ItemPedidoResponseDTO(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getNome(),
                        item.getImagemUrl(),
                        item.getPrecoHistorico(),
                        item.getQuantidade(), java.util.List.copyOf(item.getAdicionais())
                )).toList() : List.of(),
                pedido.getDesconto(),
                pedido.getCupomId(),
                null,
                null,
                false
        );
    }

    public static PedidoResponseDTO comDetalhesEntregador(Pedido pedido, br.com.nhac.backend_nhac.domain.entrega.dto.EntregadorPedidoDTO entregadorDto, boolean entregadorAvaliado) {
        String codigo = null;
        if (pedido.getStatus() == StatusPedido.SAIU_ENTREGA) {
            codigo = pedido.getCodigoEntrega();
        }
        
        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getLoja().getId(),
                pedido.getLoja().getNome(),
                pedido.getValorTotal(),
                pedido.getTaxaFrete(),
                pedido.getFormaPagamento(),
                pedido.getTrocoPara(),
                pedido.getObservacao(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getEnderecoEntrega() != null ? new EnderecoEntregaResponseDTO(
                        pedido.getEnderecoEntrega().getRua(),
                        pedido.getEnderecoEntrega().getNumero(),
                        pedido.getEnderecoEntrega().getBairro(),
                        pedido.getEnderecoEntrega().getCidade(),
                        pedido.getEnderecoEntrega().getEstado(),
                        pedido.getEnderecoEntrega().getCep(),
                        pedido.getEnderecoEntrega().getComplemento()
                ) : null,
                pedido.getItens() != null ? pedido.getItens().stream().map(item -> new ItemPedidoResponseDTO(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getNome(),
                        item.getImagemUrl(),
                        item.getPrecoHistorico(),
                        item.getQuantidade(), java.util.List.copyOf(item.getAdicionais())
                )).toList() : List.of(),
                pedido.getDesconto(),
                pedido.getCupomId(),
                codigo,
                entregadorDto,
                entregadorAvaliado
        );
    }

    @Schema(description = "Endereço de entrega do pedido")
    public record EnderecoEntregaResponseDTO(
            String rua,
            String numero,
            String bairro,
            String cidade,
            String estado,
            String cep,
            String complemento
    ) {}

    @Schema(description = "Item individual do pedido")
    public record ItemPedidoResponseDTO(
            String id,
            String produtoId,
            String nome,
            String imagemUrl,
            BigDecimal preco,
            Integer quantidade, List<String> adicionais
    ) {
        public ItemPedidoResponseDTO(String id, String produtoId, String nome, String imagemUrl, BigDecimal preco, Integer quantidade) {
            this(id,produtoId,nome,imagemUrl,preco,quantidade,List.of());
        }
    }
}
