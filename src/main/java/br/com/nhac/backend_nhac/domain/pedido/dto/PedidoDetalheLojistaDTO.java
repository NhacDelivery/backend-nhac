package br.com.nhac.backend_nhac.domain.pedido.dto;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Igual a PedidoResponseDTO, mas do ponto de vista do lojista: troca o
 * lojaId/lojaNome (redundante — é sempre a própria loja de quem está logado)
 * por clienteNome/clienteTelefone, que é o dado que falta pra atender o pedido.
 */
@Schema(description = "Dados detalhados de um pedido, do ponto de vista do lojista")
public record PedidoDetalheLojistaDTO(
        @Schema(description = "ID do pedido") String id,
        @Schema(description = "Nome do cliente que fez o pedido") String clienteNome,
        @Schema(description = "Telefone do cliente que fez o pedido") String clienteTelefone,
        @Schema(description = "Valor total do pedido") BigDecimal valorTotal,
        @Schema(description = "Taxa de frete cobrada") BigDecimal taxaFrete,
        @Schema(description = "Forma de pagamento") String formaPagamento,
        @Schema(description = "Troco para") BigDecimal trocoPara,
        @Schema(description = "Observações") String observacao,
        @Schema(description = "Status atual do pedido") StatusPedido status,
        @Schema(description = "Data e hora da criação") Instant criadoEm,
        @Schema(description = "Endereço onde será entregue") PedidoResponseDTO.EnderecoEntregaResponseDTO enderecoEntrega,
        @Schema(description = "Itens do pedido") List<PedidoResponseDTO.ItemPedidoResponseDTO> itens
) {
    public PedidoDetalheLojistaDTO(Pedido pedido, String clienteNome, String clienteTelefone) {
        this(
                pedido.getId(),
                clienteNome,
                clienteTelefone,
                pedido.getValorTotal(),
                pedido.getTaxaFrete(),
                pedido.getFormaPagamento(),
                pedido.getTrocoPara(),
                pedido.getObservacao(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getEnderecoEntrega() != null ? new PedidoResponseDTO.EnderecoEntregaResponseDTO(
                        pedido.getEnderecoEntrega().getRua(),
                        pedido.getEnderecoEntrega().getNumero(),
                        pedido.getEnderecoEntrega().getBairro(),
                        pedido.getEnderecoEntrega().getCidade(),
                        pedido.getEnderecoEntrega().getEstado(),
                        pedido.getEnderecoEntrega().getCep(),
                        pedido.getEnderecoEntrega().getComplemento()
                ) : null,
                pedido.getItens() != null ? pedido.getItens().stream().map(item -> new PedidoResponseDTO.ItemPedidoResponseDTO(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getNome(),
                        item.getImagemUrl(),
                        item.getPrecoHistorico(),
                        item.getQuantidade(),
                        List.copyOf(item.getAdicionais())
                )).toList() : List.of()
        );
    }
}
