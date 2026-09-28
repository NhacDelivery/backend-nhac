package br.com.nhac.backend_nhac.domain.produto.dto;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProdutoCreateDTO(

        @Schema(description = "Nome do produto que vai aparecer no cardápio", example = "Hossomaki de Salmão")
        @NotBlank(message = "O nome do produto não pode estar vazio.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @Schema(description = "Descrição detalhada dos ingredientes", example = "Delicioso rolinho de arroz com salmão fresco e alga.")
        String descricao,

        @Schema(description = "Preço final do produto", example = "25.50")
        @NotNull(message = "O preço é obrigatório.")
        @PositiveOrZero(message = "O preço não pode ser negativo.")
        BigDecimal preco,

        @Schema(description = "Categoria para agrupar no menu do Flutter", example = "Sushi")
        @NotBlank(message = "A categoria do menu é obrigatória.")
        String categoriaMenu,

        @Schema(description = "URL da imagem no Firebase Storage", example = "https://firebasestorage.googleapis.com/.../hossomaki.png")
        String imagemUrl,


        @Schema(description = "Peso ou porção para exibição", example = "200g")
        String peso,

        @Schema(description = "Percentual de desconto ativo (0 a 100)", example = "10")
        @Min(value = 0, message = "O percentual de desconto não pode ser negativo.")
        @Max(value = 100, message = "O percentual de desconto não pode ser maior que 100.")
        Integer percentualDesconto,

        @Schema(description = "Lista de grupos de adicionais do produto", example = "[]")
        List<@jakarta.validation.Valid GrupoAdicionalDTO> adicionais,

        @Schema(description = "Quantidade em estoque. Se não informado, o produto nasce com o valor padrão do sistema (100).", example = "50")
        @PositiveOrZero(message = "O estoque não pode ser negativo.")
        Integer estoque
) {

        public Produto toEntity(Loja lojaDaBaseDeDados, ProdutoRepository produtoRepository) {
                String novoId = "prod_" + UUID.randomUUID();

                Produto produto = new Produto(this, lojaDaBaseDeDados);
                produto.setId(novoId);
                if (this.estoque() != null) {
                        produto.setEstoque(this.estoque());
                }

                return produto;
        }
}
