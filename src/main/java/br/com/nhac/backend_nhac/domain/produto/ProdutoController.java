package br.com.nhac.backend_nhac.domain.produto;

import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoCreateDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.ErroPadraoDTO;
import br.com.nhac.backend_nhac.domain.produto.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Endpoints para gerenciamento do cardápio das lojas")
public class ProdutoController {

    private final ProdutoService produtoService;

    @Autowired
    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @Operation(summary = "Cadastrar novo produto", description = "Cria um novo item no cardápio vinculado a uma loja existente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto cadastrado com sucesso no MariaDB."),

            @ApiResponse(responseCode = "400", description = "Erro de validação nos dados enviados (ex: preço negativo, nome vazio).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para cadastrar produtos.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "404", description = "Loja do usuário não encontrada ou loja fechada.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('LOJISTA', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProdutoResumoDTO> cadastrarProduto(@Valid @RequestBody ProdutoCreateDTO dto,
                                                              @AuthenticationPrincipal Usuario usuarioLogado) {

        br.com.nhac.backend_nhac.domain.produto.Produto produtoSalvo = produtoService.cadastrarProduto(dto, usuarioLogado);
        ProdutoResumoDTO resumoDTO = new ProdutoResumoDTO(produtoSalvo);

        return ResponseEntity.status(HttpStatus.CREATED).body(resumoDTO);
    }

    @Operation(summary = "Buscar produto por ID", description = "Retorna os dados completos de um único produto ativo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto encontrado com sucesso."),

            @ApiResponse(responseCode = "404", description = "Produto não encontrado ou inativo.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/promocoes")
    public ResponseEntity<Page<ProdutoResumoDTO>> listarPromocoes(
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(produtoService.listarPromocoes(pageable));
    }

    @GetMapping("/{produtoId}")
    public ResponseEntity<ProdutoResumoDTO> buscarProdutoPorId(@PathVariable String produtoId) {
        ProdutoResumoDTO produto = produtoService.buscarProdutoPorId(produtoId);
        return ResponseEntity.ok(produto);
    }

    @Operation(summary = "Listar produtos com filtros dinâmicos", description = "Aceita filtros opcionais de loja, preço máximo, categoria ou nome. Retorna paginação.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Listagem de produtos retornada com sucesso."),

            @ApiResponse(responseCode = "404", description = "A loja especificada pelo ID não existe.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ProdutoResumoDTO>> listarProdutos(
            @RequestParam(required = false) String lojaId,
            @RequestParam(required = false) BigDecimal precoMaximo,
            @RequestParam(required = false) String categoriaMenu,
            @RequestParam(required = false) String nome,
            Pageable pageable) {

        Page<ProdutoResumoDTO> page = produtoService.listarProdutos(lojaId, precoMaximo, categoriaMenu, nome, pageable);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Editar um produto existente", description = "Atualiza os dados de um produto dado o seu ID. É necessário passar todos os campos obrigatórios.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso."),

            @ApiResponse(responseCode = "400", description = "Erro de validação nos dados enviados.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para editar este produto.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PutMapping("/{produtoId}")
    @PreAuthorize("hasAnyRole('LOJISTA', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProdutoResumoDTO> atualizarProduto(
            @PathVariable String produtoId,
            @Valid @RequestBody br.com.nhac.backend_nhac.domain.produto.dto.ProdutoUpdateDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        ProdutoResumoDTO produtoAtualizado = produtoService.atualizarProduto(produtoId, dto, usuarioLogado);
        return ResponseEntity.ok(produtoAtualizado);
    }

    @Operation(summary = "Desativar um produto (Soft Delete)", description = "Marca o produto como inativo para que deixe de aparecer para venda. Mantém o histórico no banco de dados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto desativado com sucesso."),

            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para desativar este produto.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @DeleteMapping("/{produtoId}")
    @PreAuthorize("hasAnyRole('LOJISTA', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<Void> desativarProduto(@PathVariable String produtoId,
                                                  @AuthenticationPrincipal Usuario usuarioLogado) {
        produtoService.desativarProduto(produtoId, usuarioLogado);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reativar um produto", description = "Marca o produto como ativo novamente para voltar a aparecer à venda. Apenas o dono da loja ou ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto reativado com sucesso."),
            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para reativar este produto.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PatchMapping("/{produtoId}/ativar")
    @PreAuthorize("hasAnyRole('LOJISTA', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProdutoResumoDTO> ativarProduto(@PathVariable String produtoId,
                                                          @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(produtoService.ativarProduto(produtoId, usuarioLogado));
    }

    @Operation(summary = "Repor/ajustar estoque de um produto", description = "Atualiza a quantidade em estoque de um produto para o valor absoluto informado (não é incremento). Pensado para reposição rápida sem precisar reenviar o produto inteiro.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estoque atualizado com sucesso."),
            @ApiResponse(responseCode = "400", description = "Erro de validação (ex: estoque negativo).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para repor o estoque deste produto.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PatchMapping("/{produtoId}/estoque")
    @PreAuthorize("hasAnyRole('LOJISTA', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProdutoResumoDTO> atualizarEstoque(
            @PathVariable String produtoId,
            @Valid @RequestBody br.com.nhac.backend_nhac.domain.produto.dto.AtualizarEstoqueDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(produtoService.atualizarEstoque(produtoId, dto.estoque(), usuarioLogado));
    }

    @Operation(summary = "Resumo de avaliações de um produto", description = "Retorna a média de notas e o total de avaliações de um produto.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resumo retornado com sucesso."),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/{produtoId}/avaliacoes/resumo")
    public ResponseEntity<br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO> buscarResumoAvaliacoes(@PathVariable String produtoId) {
        br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO resumo = produtoService.buscarResumoAvaliacoes(produtoId);
        return ResponseEntity.ok(resumo);
    }
}