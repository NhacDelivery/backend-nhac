package br.com.nhac.backend_nhac.domain.produto;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoCreateDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;


@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final LojaRepository lojaRepository;
    private final LojaAccessService lojaAccessService;

    public ProdutoService(ProdutoRepository produtoRepository, LojaRepository lojaRepository, LojaAccessService lojaAccessService) {
        this.produtoRepository = produtoRepository;
        this.lojaRepository = lojaRepository;
        this.lojaAccessService = lojaAccessService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTOS, key = "'promocoes:' + #p0", condition = "#p0.isPaged() && #p0.pageNumber < 20 && #p0.pageSize <= 100")
    public Page<ProdutoResumoDTO> listarPromocoes(Pageable pageable) {
        return produtoRepository.findPromocoes(pageable).map(ProdutoResumoDTO::new);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTOS, key = "'cards:' + #p0 + ':' + #p1 + ':' + #p2 + ':' + #p3 + ':' + #p4",
            condition = "#p4.isPaged() && #p4.pageNumber < 20 && #p4.pageSize <= 100")
    public Page<ProdutoResumoDTO> listarCards(String lojaId, BigDecimal precoMaximo, String categoriaMenu,
            String nome, Pageable pageable) {
        return produtoRepository.findAllWithFilters(lojaId, categoriaMenu, nome, precoMaximo, pageable)
                .map(produto -> new ProdutoResumoDTO(produto, false));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTOS, key = "'cards-promocoes:' + #p0",
            condition = "#p0.isPaged() && #p0.pageNumber < 20 && #p0.pageSize <= 100")
    public Page<ProdutoResumoDTO> listarCardsPromocoes(Pageable pageable) {
        return produtoRepository.findPromocoes(pageable).map(produto -> new ProdutoResumoDTO(produto, false));
    }

    @Transactional
    @CacheEvict(cacheNames = {PRODUTOS, PRODUTO}, allEntries = true)
    public Produto cadastrarProduto(ProdutoCreateDTO dto, Usuario usuarioLogado) {
        // ADMIN tem bypass na checagem de ownership, mas ainda precisa de uma loja associada
        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");

        // obterLojaAcessivel resolve tanto dono (LOJISTA) quanto FUNCIONARIO vinculado à loja
        Loja lojaDoUsuario = lojaAccessService.obterLojaAcessivel(usuarioLogado);

        if (!lojaDoUsuario.isAberto() && !isAdmin) {
            throw new RegraDeNegocioException("Não é possível cadastrar produtos em uma loja fechada.");
        }

        Produto novoProduto = dto.toEntity(lojaDoUsuario, produtoRepository);
        return produtoRepository.save(novoProduto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTO)
    public ProdutoResumoDTO buscarProdutoPorId(String produtoId) {
        Produto produto = produtoRepository.findByIdAndIsAtivoTrue(produtoId)
                .orElseThrow(() -> new IdNaoEncontradoException(
                        "O produto com o id: " + produtoId + " não foi encontrado."));

        return new ProdutoResumoDTO(produto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTOS, condition = "#p4.isPaged() && #p4.pageNumber < 20 && #p4.pageSize <= 100")
    public Page<ProdutoResumoDTO> listarProdutos(String lojaId, BigDecimal precoMaximo, String categoriaMenu, String nome, Pageable pageable) {
        Page<Produto> produtos = produtoRepository.findAllWithFilters(lojaId, categoriaMenu, nome, precoMaximo, pageable);
        return produtos.map(ProdutoResumoDTO::new);
    }

    @Transactional
    @CacheEvict(cacheNames = {PRODUTOS, PRODUTO}, allEntries = true)
    public ProdutoResumoDTO atualizarProduto(String id, br.com.nhac.backend_nhac.domain.produto.dto.ProdutoUpdateDTO dto, Usuario usuarioLogado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("O produto com o id: " + id + " não foi encontrado."));

        // ADMIN tem bypass na checagem de ownership; dono ou funcionário da loja também passam
        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, produto.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para editar este produto.");
        }

        if (!produto.getLoja().isAberto() && !isAdmin) {
            throw new RegraDeNegocioException("Não é possível editar produtos de uma loja fechada.");
        }

        produto.setNome(dto.nome());
        produto.setDescricao(dto.descricao());
        produto.setPreco(dto.preco());
        produto.setCategoriaMenu(dto.categoriaMenu());
        produto.setImagemUrl(dto.imagemUrl());
        produto.setPeso(dto.peso());
        produto.setPercentualDesconto(dto.percentualDesconto());
        produto.setAtivo(dto.isAtivo());
        produto.substituirAdicionais(dto.adicionais());
        if (dto.estoque() != null) {
            produto.setEstoque(dto.estoque());
        }

        produtoRepository.save(produto);

        return new ProdutoResumoDTO(produto);
    }

    @Transactional
    @CacheEvict(cacheNames = {PRODUTOS, PRODUTO}, allEntries = true)
    public void desativarProduto(String id, Usuario usuarioLogado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("O produto com o id: " + id + " não foi encontrado."));

        // ADMIN tem bypass na checagem de ownership; dono ou funcionário da loja também passam
        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, produto.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para desativar este produto.");
        }

        if (!produto.getLoja().isAberto() && !isAdmin) {
            throw new RegraDeNegocioException("Não é possível desativar produtos de uma loja fechada.");
        }

        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(cacheNames = {PRODUTOS, PRODUTO}, allEntries = true)
    public ProdutoResumoDTO ativarProduto(String id, Usuario usuarioLogado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("O produto com o id: " + id + " não foi encontrado."));

        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, produto.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para reativar este produto.");
        }

        if (!produto.getLoja().isAberto() && !isAdmin) {
            throw new RegraDeNegocioException("Não é possível reativar produtos de uma loja fechada.");
        }

        produto.setAtivo(true);
        produtoRepository.save(produto);
        return new ProdutoResumoDTO(produto);
    }

    @Transactional
    public ProdutoResumoDTO atualizarEstoque(String id, Integer novoEstoque, Usuario usuarioLogado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("O produto com o id: " + id + " não foi encontrado."));

        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, produto.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para repor o estoque deste produto.");
        }

        produto.setEstoque(novoEstoque);
        produtoRepository.save(produto);
        return new ProdutoResumoDTO(produto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUTO_AVALIACOES)
    public br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO buscarResumoAvaliacoes(String produtoId) {
        if (!produtoRepository.existsById(produtoId)) {
            throw new IdNaoEncontradoException("O produto com o id: " + produtoId + " nǜo foi encontrado.");
        }
        return produtoRepository.getResumoAvaliacoesPorProdutoId(produtoId);
    }
}
