package br.com.nhac.backend_nhac.domain.produto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.GrupoAdicionalDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ItemAdicionalDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoCreateDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoResumoDTO;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoUpdateDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private LojaAccessService lojaAccessService;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    void cardsNaoCarregamAdicionaisMasDetalhePreservaContrato() {
        Produto produto = org.mockito.Mockito.spy(produtoDeTeste());
        Pageable pagina = PageRequest.of(0, 50);
        when(produtoRepository.findAllWithFilters(null, null, null, null, pagina))
                .thenReturn(new PageImpl<>(List.of(produto)));
        var cards = produtoService.listarCards(null, null, null, null, pagina);
        assertEquals(produto.getId(), cards.getContent().getFirst().id());
        assertTrue(cards.getContent().getFirst().adicionais().isEmpty());
        verify(produto, never()).getAdicionais();
        org.mockito.Mockito.clearInvocations(produto);
        new ProdutoResumoDTO(produto);
        verify(produto, org.mockito.Mockito.atLeastOnce()).getAdicionais();
    }

    @BeforeEach
    void configurarAcessoDaLoja() {
        lenient().when(lojaAccessService.obterLojaAcessivel(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario usuario = invocation.getArgument(0);
                    return criarLoja("loja_1", usuario.getId(), true);
                });
        lenient().when(lojaAccessService.temAcessoALoja(any(Usuario.class), anyString()))
                .thenAnswer(invocation -> {
                    Usuario usuario = invocation.getArgument(0);
                    return "usuario_lojista_1".equals(usuario.getId());
                });
    }

    private Usuario criarUsuario(String id, String papel) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail("teste@nhac.com");
        usuario.setNome("Teste");
        if ("ADMIN".equals(papel)) {
            usuario.setPapel(Papel.ADMIN);
        } else {
            usuario.setPapel(Papel.LOJISTA);
        }
        return usuario;
    }

    private Loja criarLoja(String id, String usuarioId, boolean aberto) {
        Loja loja = new Loja();
        loja.setId(id);
        loja.setAberto(aberto);
        loja.setUsuarioId(usuarioId);
        return loja;
    }

    private Produto produtoDeTeste() {
        Loja loja = criarLoja("loja_1", "usuario_lojista_1", true);

        Produto produto = new Produto();
        produto.setId("produto_1");
        produto.setLoja(loja);
        produto.setNome("Hossomaki");
        produto.setDescricao("Descrição do produto");
        produto.setPreco(new BigDecimal("25.50"));
        produto.setCategoriaMenu("Sushi");
        produto.setImagemUrl("url");
        produto.setPeso("200g");
        produto.setPercentualDesconto(10);
        produto.setAtivo(true);
        return produto;
    }

    @Test
    @DisplayName("Deve cadastrar produto com sucesso quando a loja for encontrada no banco")
    void deveCadastrarProdutoComSucesso() {
        Usuario usuarioLojista = criarUsuario("usuario_123", "LOJISTA");
        Loja lojaFalsa = criarLoja("loja_123", "usuario_123", true);

        ProdutoCreateDTO dto = new ProdutoCreateDTO(
                "Hossomaki", "Descrição", new BigDecimal("25.50"),
                "Sushi", "url", "200g", 10, null, 50
        );

        Produto produtoSalvo = new Produto();
        produtoSalvo.setId("produto_gerado_123");
        produtoSalvo.setNome("Hossomaki");

        when(lojaAccessService.obterLojaAcessivel(usuarioLojista)).thenReturn(lojaFalsa);
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvo);

        Produto resultado = produtoService.cadastrarProduto(dto, usuarioLojista);

        assertNotNull(resultado);
        assertEquals("Hossomaki", resultado.getNome());

        verify(lojaAccessService, times(1)).obterLojaAcessivel(usuarioLojista);
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve explodir exceção quando usuário não tiver loja cadastrada")
    void deveLancarExcecaoQuandoUsuarioNaoTiverLoja() {
        Usuario usuarioSemLoja = criarUsuario("usuario_sem_loja", "LOJISTA");

        ProdutoCreateDTO dto = new ProdutoCreateDTO(
                "Hossomaki", "Descrição", new BigDecimal("25.50"),
                "Sushi", "url", "200g", 10, null, null
        );

        when(lojaAccessService.obterLojaAcessivel(usuarioSemLoja))
                .thenThrow(new LojaNaoEncontradaException());

        Exception excecao = assertThrows(LojaNaoEncontradaException.class,
                () -> produtoService.cadastrarProduto(dto, usuarioSemLoja));

        assertEquals("Loja do usuário não encontrada.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException quando tentar cadastrar produto em loja fechada")
    void deveLancarExcecaoQuandoLojaEstiverFechada() {
        Usuario usuarioLojista = criarUsuario("usuario_123", "LOJISTA");
        Loja lojaFechada = criarLoja("loja_fechada", "usuario_123", false);

        ProdutoCreateDTO dto = new ProdutoCreateDTO(
                "Hossomaki", "Descrição", new BigDecimal("25.50"),
                "Sushi", "url", "200g", 10, null, null
        );

        when(lojaAccessService.obterLojaAcessivel(usuarioLojista)).thenReturn(lojaFechada);

        Exception excecao = assertThrows(RegraDeNegocioException.class, () -> produtoService.cadastrarProduto(dto, usuarioLojista));

        assertEquals("Não é possível cadastrar produtos em uma loja fechada.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("ADMIN deve poder cadastrar produto mesmo em loja fechada")
    void adminDeveCadastrarProdutoEmLojaFechada() {
        Usuario usuarioAdmin = criarUsuario("admin_123", "ADMIN");
        Loja lojaFechada = criarLoja("loja_fechada", "admin_123", false);

        ProdutoCreateDTO dto = new ProdutoCreateDTO(
                "Hossomaki", "Descrição", new BigDecimal("25.50"),
                "Sushi", "url", "200g", 10, null, 100
        );

        Produto produtoSalvo = new Produto();
        produtoSalvo.setId("produto_gerado_123");
        produtoSalvo.setNome("Hossomaki");

        when(lojaAccessService.obterLojaAcessivel(usuarioAdmin)).thenReturn(lojaFechada);
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvo);

        Produto resultado = produtoService.cadastrarProduto(dto, usuarioAdmin);

        assertNotNull(resultado);
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve listar produtos filtrando por nome quando o parâmetro for informado")
    void deveListarProdutosFiltrandoPorNome() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produto> pagina = new PageImpl<>(List.of(produtoDeTeste()), pageable, 1);

        when(produtoRepository.findAllWithFilters(null, null, "hosso", null, pageable)).thenReturn(pagina);

        Page<ProdutoResumoDTO> resultado = produtoService.listarProdutos(null, null, null, "hosso", pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(produtoRepository, times(1)).findAllWithFilters(null, null, "hosso", null, pageable);
    }

    @Test
    @DisplayName("Deve listar produtos filtrando por preço máximo quando o parâmetro for informado")
    void deveListarProdutosFiltrandoPorPrecoMaximo() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produto> pagina = new PageImpl<>(List.of(produtoDeTeste()), pageable, 1);
        BigDecimal precoMaximo = new BigDecimal("30.00");

        when(produtoRepository.findAllWithFilters(null, null, null, precoMaximo, pageable)).thenReturn(pagina);

        Page<ProdutoResumoDTO> resultado = produtoService.listarProdutos(null, precoMaximo, null, null, pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(produtoRepository, times(1)).findAllWithFilters(null, null, null, precoMaximo, pageable);
    }

    @Test
    @DisplayName("Deve listar produtos filtrando por categoria quando o parâmetro for informado")
    void deveListarProdutosFiltrandoPorCategoria() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produto> pagina = new PageImpl<>(List.of(produtoDeTeste()), pageable, 1);

        when(produtoRepository.findAllWithFilters(null, "Sushi", null, null, pageable)).thenReturn(pagina);

        Page<ProdutoResumoDTO> resultado = produtoService.listarProdutos(null, null, "Sushi", null, pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(produtoRepository, times(1)).findAllWithFilters(null, "Sushi", null, null, pageable);
    }

    @Test
    @DisplayName("Deve listar produtos filtrando por loja quando o parâmetro for informado")
    void deveListarProdutosFiltrandoPorLoja() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produto> pagina = new PageImpl<>(List.of(produtoDeTeste()), pageable, 1);

        when(produtoRepository.findAllWithFilters("loja_1", null, null, null, pageable)).thenReturn(pagina);

        Page<ProdutoResumoDTO> resultado = produtoService.listarProdutos("loja_1", null, null, null, pageable);

        assertEquals(1, resultado.getTotalElements());
        assertEquals("loja_1", resultado.getContent().get(0).lojaId());
        assertEquals("Hossomaki", resultado.getContent().get(0).nome());
        verify(produtoRepository, times(1)).findAllWithFilters("loja_1", null, null, null, pageable);
    }

    @Test
    @DisplayName("Deve listar todos os produtos quando nenhum filtro for informado")
    void deveListarTodosOsProdutosSemFiltros() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Produto> pagina = new PageImpl<>(List.of(produtoDeTeste()), pageable, 1);

        when(produtoRepository.findAllWithFilters(null, null, null, null, pageable)).thenReturn(pagina);

        Page<ProdutoResumoDTO> resultado = produtoService.listarProdutos(null, null, null, null, pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(produtoRepository, times(1)).findAllWithFilters(null, null, null, null, pageable);
    }

    @Test
    @DisplayName("Deve retornar os dados do produto quando ele existir e estiver ativo")
    void deveBuscarProdutoPorIdComSucesso() {
        when(produtoRepository.findByIdAndIsAtivoTrue("produto_1")).thenReturn(Optional.of(produtoDeTeste()));

        ProdutoResumoDTO resultado = produtoService.buscarProdutoPorId("produto_1");

        assertEquals("produto_1", resultado.id());
        assertEquals("Hossomaki", resultado.nome());
        assertEquals("loja_1", resultado.lojaId());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException quando o produto não existir ou estiver inativo")
    void deveLancarExcecaoAoBuscarProdutoInexistenteOuInativo() {
        when(produtoRepository.findByIdAndIsAtivoTrue("produto_fantasma")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> produtoService.buscarProdutoPorId("produto_fantasma"));

        assertEquals("O produto com o id: produto_fantasma não foi encontrado.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve atualizar produto com sucesso quando ele for encontrado e usuário for dono")
    void deveAtualizarProdutoComSucesso() {
        Usuario usuarioDono = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        ProdutoUpdateDTO dto = new ProdutoUpdateDTO(
                "Hossomaki Editado", "Descrição editada", new BigDecimal("29.90"),
                "Sushi", "nova-url", "300g", 15, true, null, 30
        );

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        ProdutoResumoDTO resultado = produtoService.atualizarProduto("produto_1", dto, usuarioDono);

        assertEquals("Hossomaki Editado", resultado.nome());
        assertEquals(new BigDecimal("29.90"), resultado.preco());

        verify(produtoRepository, times(1)).findById("produto_1");
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    void devePersistirEPermitirRemoverAdicionaisNaEdicao() {
        Usuario dono = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produto = produtoDeTeste();
        var grupo = new GrupoAdicionalDTO("Molhos", false, 0, 1,
                List.of(new ItemAdicionalDTO("Barbecue", new BigDecimal("2.50"))));
        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produto));
        when(produtoRepository.save(produto)).thenReturn(produto);

        produtoService.atualizarProduto("produto_1", new ProdutoUpdateDTO(
                "Hossomaki", "Descrição", BigDecimal.TEN, "Sushi", "url", null, 0,
                true, List.of(grupo), 10), dono);

        assertEquals(1, produto.getAdicionais().size());
        assertEquals(produto, produto.getAdicionais().get(0).getProduto());
        assertEquals(new BigDecimal("2.50"), produto.getAdicionais().get(0).getItens().get(0).getPreco());
        assertEquals(produto.getAdicionais().get(0),
                produto.getAdicionais().get(0).getItens().get(0).getGrupoAdicional());

        produtoService.atualizarProduto("produto_1", new ProdutoUpdateDTO(
                "Hossomaki", "Descrição", BigDecimal.TEN, "Sushi", "url", null, 0,
                true, List.of(), 10), dono);
        assertTrue(produto.getAdicionais().isEmpty());
    }

    @Test
    void deveMontarAdicionaisAoCriarProduto() {
        Loja loja = criarLoja("loja_1", "usuario_lojista_1", true);
        var grupo = new GrupoAdicionalDTO("Molhos", true, 1, 1,
                List.of(new ItemAdicionalDTO("Barbecue", new BigDecimal("2.50"))));
        ProdutoCreateDTO dados = new ProdutoCreateDTO("Hambúrguer", "Descrição",
                BigDecimal.TEN, "Lanches", "url", null, null, List.of(grupo), 10);

        Produto produto = dados.toEntity(loja, produtoRepository);
        assertEquals(1, produto.getAdicionais().size());
        assertEquals(produto, produto.getAdicionais().get(0).getProduto());
        assertEquals(1, produto.getAdicionais().get(0).getItens().size());
    }

    @Test
    @DisplayName("ADMIN deve poder atualizar produto de qualquer loja")
    void adminDeveAtualizarProdutoDeQualquerLoja() {
        Usuario usuarioAdmin = criarUsuario("admin_123", "ADMIN");
        Produto produtoOriginal = produtoDeTeste();
        ProdutoUpdateDTO dto = new ProdutoUpdateDTO(
                "Hossomaki Editado", "Descrição editada", new BigDecimal("29.90"),
                "Sushi", "nova-url", "300g", 15, true, null, 30
        );

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        ProdutoResumoDTO resultado = produtoService.atualizarProduto("produto_1", dto, usuarioAdmin);

        assertNotNull(resultado);
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException quando lojista tentar atualizar produto de outra loja")
    void deveLancarAcessoNegadoAoAtualizarProdutoDeOutraLoja() {
        Usuario usuarioDeOutraLoja = criarUsuario("usuario_de_outra_loja", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        ProdutoUpdateDTO dto = new ProdutoUpdateDTO(
                "Hossomaki Editado", "Descrição editada", new BigDecimal("29.90"),
                "Sushi", "nova-url", "300g", 15, true, null, 30
        );

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        Exception excecao = assertThrows(AcessoNegadoException.class,
                () -> produtoService.atualizarProduto("produto_1", dto, usuarioDeOutraLoja));

        assertEquals("Acesso negado: você não tem permissão para editar este produto.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao tentar atualizar produto inexistente")
    void deveLancarExcecaoAoAtualizarProdutoInexistente() {
        Usuario usuarioLojista = criarUsuario("usuario_lojista_1", "LOJISTA");
        ProdutoUpdateDTO dto = new ProdutoUpdateDTO(
                "Hossomaki Editado", "Descrição editada", new BigDecimal("29.90"),
                "Sushi", "nova-url", "300g", 15, true, null, 30
        );

        when(produtoRepository.findById("produto_fantasma")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> produtoService.atualizarProduto("produto_fantasma", dto, usuarioLojista));

        assertEquals("O produto com o id: produto_fantasma não foi encontrado.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar atualizar produto de loja fechada")
    void deveLancarExcecaoAoAtualizarProdutoDeLojaFechada() {
        Usuario usuarioLojista = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.getLoja().setAberto(false);

        ProdutoUpdateDTO dto = new ProdutoUpdateDTO(
                "Hossomaki Editado", "Descrição editada", new BigDecimal("29.90"),
                "Sushi", "nova-url", "300g", 15, true, null, 30
        );

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> produtoService.atualizarProduto("produto_1", dto, usuarioLojista));

        assertEquals("Não é possível editar produtos de uma loja fechada.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve desativar produto com sucesso quando ele for encontrado e usuário for dono")
    void deveDesativarProdutoComSucesso() {
        Usuario usuarioDono = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(true);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        produtoService.desativarProduto("produto_1", usuarioDono);

        assertFalse(produtoOriginal.isAtivo());

        verify(produtoRepository, times(1)).findById("produto_1");
        verify(produtoRepository, times(1)).save(produtoOriginal);
    }

    @Test
    @DisplayName("ADMIN deve poder desativar produto de qualquer loja")
    void adminDeveDesativarProdutoDeQualquerLoja() {
        Usuario usuarioAdmin = criarUsuario("admin_123", "ADMIN");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(true);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        produtoService.desativarProduto("produto_1", usuarioAdmin);

        assertFalse(produtoOriginal.isAtivo());
        verify(produtoRepository, times(1)).save(produtoOriginal);
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException quando lojista tentar desativar produto de outra loja")
    void deveLancarAcessoNegadoAoDesativarProdutoDeOutraLoja() {
        Usuario usuarioDeOutraLoja = criarUsuario("usuario_de_outra_loja", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(true);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        Exception excecao = assertThrows(AcessoNegadoException.class,
                () -> produtoService.desativarProduto("produto_1", usuarioDeOutraLoja));

        assertEquals("Acesso negado: você não tem permissão para desativar este produto.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao tentar desativar produto inexistente")
    void deveLancarExcecaoAoDesativarProdutoInexistente() {
        Usuario usuarioLojista = criarUsuario("usuario_lojista_1", "LOJISTA");
        when(produtoRepository.findById("produto_fantasma")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> produtoService.desativarProduto("produto_fantasma", usuarioLojista));

        assertEquals("O produto com o id: produto_fantasma não foi encontrado.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar desativar produto de loja fechada")
    void deveLancarExcecaoAoDesativarProdutoDeLojaFechada() {
        Usuario usuarioLojista = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.getLoja().setAberto(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> produtoService.desativarProduto("produto_1", usuarioLojista));

        assertEquals("Não é possível desativar produtos de uma loja fechada.", excecao.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("ADMIN deve poder desativar produto mesmo em loja fechada")
    void adminDeveDesativarProdutoEmLojaFechada() {
        Usuario usuarioAdmin = criarUsuario("admin_123", "ADMIN");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(true);
        produtoOriginal.getLoja().setAberto(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        produtoService.desativarProduto("produto_1", usuarioAdmin);

        assertFalse(produtoOriginal.isAtivo());
        verify(produtoRepository, times(1)).save(produtoOriginal);
    }

    @Test
    @DisplayName("Deve retornar resumo de avaliações de um produto")
    void deveBuscarResumoAvaliacoesProduto() {
        when(produtoRepository.existsById("produto_1")).thenReturn(true);
        when(produtoRepository.getResumoAvaliacoesPorProdutoId("produto_1"))
                .thenReturn(new ProdutoAvaliacaoResumoDTO(15L, 4.8));

        ProdutoAvaliacaoResumoDTO resumo = produtoService.buscarResumoAvaliacoes("produto_1");

        assertNotNull(resumo);
        assertEquals(15L, resumo.totalAvaliacoes());
        assertEquals(4.8, resumo.mediaNotas());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao buscar resumo de avaliações de produto inexistente")
    void deveLancarExcecaoBuscarResumoAvaliacoesProdutoInexistente() {
        when(produtoRepository.existsById("produto_fantasma")).thenReturn(false);

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> produtoService.buscarResumoAvaliacoes("produto_fantasma"));

        assertTrue(excecao.getMessage().contains("produto_fantasma"));
    }

    @Test
    @DisplayName("Deve reativar produto com sucesso quando ele for encontrado e usuário for o dono")
    void deveAtivarProdutoComSucesso() {
        Usuario usuarioDono = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        ProdutoResumoDTO resumo = produtoService.ativarProduto("produto_1", usuarioDono);

        assertNotNull(resumo);
        assertTrue(produtoOriginal.isAtivo());
        verify(produtoRepository, times(1)).save(produtoOriginal);
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException ao tentar reativar produto de outro lojista")
    void deveLancarAcessoNegadoAoAtivarProdutoDeOutraLoja() {
        Usuario usuarioInvasor = criarUsuario("usuario_invasor", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        assertThrows(AcessoNegadoException.class,
                () -> produtoService.ativarProduto("produto_1", usuarioInvasor));
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("ADMIN deve poder reativar produto de qualquer loja")
    void adminDeveAtivarProdutoDeQualquerLoja() {
        Usuario admin = criarUsuario("admin_123", "ADMIN");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.setAtivo(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoOriginal);

        ProdutoResumoDTO resumo = produtoService.ativarProduto("produto_1", admin);

        assertNotNull(resumo);
        assertTrue(produtoOriginal.isAtivo());
        verify(produtoRepository, times(1)).save(produtoOriginal);
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar reativar produto de loja fechada")
    void deveLancarExcecaoAoAtivarProdutoDeLojaFechada() {
        Usuario usuarioDono = criarUsuario("usuario_lojista_1", "LOJISTA");
        Produto produtoOriginal = produtoDeTeste();
        produtoOriginal.getLoja().setAberto(false);
        produtoOriginal.setAtivo(false);

        when(produtoRepository.findById("produto_1")).thenReturn(Optional.of(produtoOriginal));

        assertThrows(RegraDeNegocioException.class,
                () -> produtoService.ativarProduto("produto_1", usuarioDono));
        verify(produtoRepository, never()).save(any(Produto.class));
    }
}
