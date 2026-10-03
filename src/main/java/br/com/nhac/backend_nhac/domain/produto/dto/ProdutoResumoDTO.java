package br.com.nhac.backend_nhac.domain.produto.dto;

import br.com.nhac.backend_nhac.domain.produto.Produto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Objeto simplificado devolvido na listagem do cardápio")
public record ProdutoResumoDTO(

        @Schema(description = "ID do produto", example = "prod_123")
        String id,

        @Schema(description = "ID da Loja", example = "loja-123")
        String lojaId,

        @Schema(description = "Nome da loja. Sempre presente, independente da loja estar aberta ou fechada no momento — usado para exibir 'Vendido por' na tela do produto sem depender de GET /lojas/{id}, que só retorna lojas abertas.", example = "Sushi Ken")
        String lojaNome,

        @Schema(description = "Nome do produto", example = "Hossomaki de Salmão")
        String nome,

        @Schema(description = "Descrição detalhada", example = "Rolinho de arroz e alga com salmão.")
        String descricao,

        @Schema(description = "Preço atual", example = "25.50")
        BigDecimal preco,

        @Schema(description = "Categoria para as abas do Flutter", example = "Sushi")
        String categoriaMenu,

        @Schema(description = "URL da imagem", example = """
                https://firebasestorage...""")
        String imagemUrl,

        @Schema(description = "peso do produto em g ou em kg", example = "23g")
        String peso,

        @Schema(description = "Percentual de desconto", example = "0")
        Integer percentualDesconto,

        @Schema(description = "Indica se a loja do produto está aberta no momento (baseado no campo isAberto da loja). "
                + "Permite ao cliente decidir a exibição sem precisar chamar GET /lojas/{id} separadamente.",
                example = "true")
        boolean lojaAberta,

        @Schema(description = "Lista de grupos de adicionais do produto")
        List<GrupoAdicionalDTO> adicionais
) {
    public ProdutoResumoDTO(Produto produto) {
        this(produto, true);
    }

    public ProdutoResumoDTO(Produto produto, boolean incluirAdicionais) {
        this(
                produto.getId(),
                produto.getLoja() != null ? produto.getLoja().getId() : null,
                produto.getLoja() != null ? produto.getLoja().getNome() : null,
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getCategoriaMenu(),
                produto.getImagemUrl(),
                produto.getPeso(),
                produto.getPercentualDesconto(),
                produto.getLoja() != null && produto.getLoja().isAberto(),
                incluirAdicionais && produto.getAdicionais() != null ? produto.getAdicionais().stream()
                    .map(grupo -> new GrupoAdicionalDTO(
                            grupo.getNome(),
                            grupo.isObrigatorio(),
                            grupo.getMinimo(),
                            grupo.getMaximo(),
                            grupo.getItens() != null ? grupo.getItens().stream()
                                .map(item -> new ItemAdicionalDTO(item.getNome(), item.getPreco()))
                                .toList() : List.of()
                    ))
                    .toList() : List.of()
        );
    }
}