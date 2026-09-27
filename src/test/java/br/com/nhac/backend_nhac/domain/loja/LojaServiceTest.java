package br.com.nhac.backend_nhac.domain.loja;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import br.com.nhac.backend_nhac.domain.loja.dto.LojaCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.AtualizarLocalizacaoLojaDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaDetalhesDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class LojaServiceTest {

    @Test
    void atualizaCoordenadasDoProprioEstabelecimento() {
        Loja loja = construirLojaCompleta("loja_1", true);
        Usuario dono = new Usuario();
        dono.setId("dono_1");
        dono.setPapel(Papel.LOJISTA);
        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(loja));
        when(lojaAccessService.temAcessoALoja(dono, "loja_1")).thenReturn(true);
        when(lojaRepository.save(loja)).thenReturn(loja);

        LojaDetalhesDTO resposta = lojaService.atualizarLocalizacao("loja_1",
                new AtualizarLocalizacaoLojaDTO(-23.55, -46.63), dono);

        assertEquals(-23.55, resposta.latitude());
        assertEquals(-46.63, resposta.longitude());
        verify(lojaRepository).save(loja);
    }

    @Test
    void atualizaEnderecoECoordenadasJuntosSemAlterarOsDemaisDados() {
        Loja loja = construirLojaCompleta("loja_1", true);
        Usuario dono = new Usuario();
        dono.setId("dono_1");
        dono.setPapel(Papel.LOJISTA);
        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(loja));
        when(lojaAccessService.temAcessoALoja(dono, "loja_1")).thenReturn(true);
        when(lojaRepository.save(loja)).thenReturn(loja);

        var novoEndereco = new LojaCreateDTO.EnderecoDTO("Rua Nova", "45", "Osasco", "SP",
                "06000-000", "Centro", "Loja B");
        LojaDetalhesDTO resposta = lojaService.atualizarLocalizacao("loja_1",
                new AtualizarLocalizacaoLojaDTO(-23.53, -46.79, novoEndereco), dono);

        assertEquals("Rua Nova", resposta.endereco().rua());
        assertEquals("45", resposta.endereco().numero());
        assertEquals(-23.53, resposta.latitude());
        assertEquals(-46.79, resposta.longitude());
        assertEquals("Sushi Ken", resposta.nome());
        verify(lojaRepository).save(loja);
    }

    @Test
    void impedeAlteracaoDeCoordenadasDeOutraLoja() {
        Loja loja = construirLojaCompleta("loja_1", true);
        Usuario estranho = new Usuario();
        estranho.setId("dono_2");
        estranho.setPapel(Papel.LOJISTA);
        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(loja));

        assertThrows(AcessoNegadoException.class, () -> lojaService.atualizarLocalizacao("loja_1",
                new AtualizarLocalizacaoLojaDTO(-23.55, -46.63), estranho));
        verify(lojaRepository, never()).save(any());
    }

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private LojaAccessService lojaAccessService;

    @Spy
    private FreteService freteService = new FreteService();

    @InjectMocks
    private LojaService lojaService;

    private Loja construirLojaCompleta(String id, boolean aberta) {
        return Loja.builder()
                .id(id)
                .nome("Sushi Ken")
                .descricao("O melhor sushi da região.")
                .categoria("Japonesa")
                .imagemUrl("http://imagem.com/banner.png")
                .isAberto(aberta)
                .dadosOperacionais(new DadosOperacionais(4.8f, new BigDecimal("5.99"), 30, 45, 150, true, false, null))
                .endereco(new EnderecoLoja("Rua das Flores", "123", "São Paulo", "SP", "01000-000", "Centro", null))
                .geoLocalizacao(new GeoLocalizacao(-23.5, -46.6, "hash123"))
                .horariosFuncionamento(new HorariosFuncionamento(
                        "18:00-23:00", "Fechado", "11:00-23:00", "11:00-23:00",
                        "11:00-23:00", "11:00-23:59", "11:00-23:59"))
                .formasPagamento(new FormasPagamento(true, true, true, true, false, false))
                .build();
    }

    @Test
    @DisplayName("Deve retornar página de lojas abertas mapeadas para LojaResumoDTO")
    void deveObterLojasPaginadasComSucesso() {
        Loja loja = construirLojaCompleta("loja_1", true);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Loja> paginaDeLojas = new PageImpl<>(List.of(loja), pageable, 1);

        when(lojaRepository.findByIsAbertoTrue(any(Pageable.class))).thenReturn(paginaDeLojas);

        Page<LojaResumoDTO> resultado = lojaService.obterLojasPaginadas(null, null, null, null, 0, 10);

        assertEquals(1, resultado.getTotalElements());
        assertEquals("loja_1", resultado.getContent().get(0).id());
        assertEquals("Sushi Ken", resultado.getContent().get(0).nome());
        verify(lojaRepository, times(1)).findByIsAbertoTrue(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve retornar página vazia quando não houver lojas abertas")
    void deveRetornarPaginaVaziaQuandoNaoHouverLojas() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Loja> paginaVazia = new PageImpl<>(List.of(), pageable, 0);

        when(lojaRepository.findByIsAbertoTrue(any(Pageable.class))).thenReturn(paginaVazia);

        Page<LojaResumoDTO> resultado = lojaService.obterLojasPaginadas(null, null, null, null, 0, 10);

        assertTrue(resultado.getContent().isEmpty());
    }

    @Test
    @DisplayName("Deve retornar os detalhes completos de uma loja aberta existente")
    void deveObterDetalhesDaLojaComSucesso() {
        Loja loja = construirLojaCompleta("loja_1", true);

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(loja));

        LojaDetalhesDTO resultado = lojaService.obterLojaId("loja_1");

        assertNotNull(resultado);
        assertEquals("loja_1", resultado.id());
        assertEquals("Sushi Ken", resultado.nome());
        assertEquals("Rua das Flores", resultado.endereco().rua());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException quando a loja não existir ou estiver fechada")
    void deveLancarExcecaoQuandoLojaNaoEncontradaOuFechada() {
        when(lojaRepository.findByIdAndIsAbertoTrue("loja_fantasma")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> lojaService.obterLojaId("loja_fantasma"));

        assertEquals("A loja com o id: loja_fantasma não foi encontrada.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve vincular a loja ao usuário autenticado e promover CLIENTE para LOJISTA")
    void deveCriarLojaVinculadaEPromoverPapelParaLojista() {
        Usuario cliente = new Usuario();
        cliente.setId("user_lojista");
        cliente.setPapel(Papel.CLIENTE);

        when(lojaRepository.save(any(Loja.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LojaResumoDTO resumo = lojaService.criarLoja(construirDtoCriacao(), cliente);

        assertTrue(resumo.id().startsWith("loja_"));
        assertEquals(Papel.LOJISTA, cliente.getPapel());
        verify(usuarioRepository).save(cliente);
        verify(lojaRepository).save(argThat(loja ->
                "user_lojista".equals(loja.getUsuarioId())
                        && loja.getId() != null
                        && loja.getId().startsWith("loja_")));
    }

    @Test
    @DisplayName("Não deve rebaixar ADMIN ao criar loja")
    void deveManterPapelAdminAoCriarLoja() {
        Usuario admin = new Usuario();
        admin.setId("user_admin");
        admin.setPapel(Papel.ADMIN);

        when(lojaRepository.save(any(Loja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        lojaService.criarLoja(construirDtoCriacao(), admin);

        assertEquals(Papel.ADMIN, admin.getPapel());
        verify(usuarioRepository, never()).save(any());
        verify(lojaRepository).save(argThat(loja -> "user_admin".equals(loja.getUsuarioId())));
    }

    @Test
    @DisplayName("Deve recusar criação de loja sem usuário autenticado")
    void deveRecusarCriacaoSemUsuarioAutenticado() {
        assertThrows(AcessoNegadoException.class, () -> lojaService.criarLoja(construirDtoCriacao(), null));
        verify(lojaRepository, never()).save(any());
    }

    @Test
@DisplayName("Deve obter minha loja quando o usuário possuir loja")
void deveObterMinhaLojaComSucesso() {
    Usuario usuario = new Usuario();
    usuario.setId("user_dono");

    Loja loja = construirLojaCompleta("loja_1", true);
    loja.setUsuarioId("user_dono");

    // AGORA o service delega a resolução pro LojaAccessService
    when(lojaAccessService.obterLojaAcessivel(usuario)).thenReturn(loja);

    LojaDetalhesDTO resultado = lojaService.obterMinhaLoja(usuario);

    assertNotNull(resultado);
    assertEquals("loja_1", resultado.id());
    assertEquals("Sushi Ken", resultado.nome());
    verify(lojaAccessService).obterLojaAcessivel(usuario);
}
  @Test
@DisplayName("Deve lançar LojaNaoEncontradaException quando o usuário não tiver loja ao consultar minha loja")
void deveLancarExcecaoQuandoMinhaLojaNaoExistir() {
    Usuario usuario = new Usuario();
    usuario.setId("user_sem_loja");

    // O LojaAccessService é quem lança a exceção agora
    when(lojaAccessService.obterLojaAcessivel(usuario))
            .thenThrow(new br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException("user_sem_loja"));

    assertThrows(br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException.class,
            () -> lojaService.obterMinhaLoja(usuario));

    verify(lojaAccessService).obterLojaAcessivel(usuario);
}

    @Test
    @DisplayName("Deve lançar AcessoNegadoException ao consultar minha loja sem estar autenticado")
    void deveLancarAcessoNegadoAoConsultarMinhaLojaSemUsuario() {
        assertThrows(AcessoNegadoException.class, () -> lojaService.obterMinhaLoja(null));
    }

  @Test
@DisplayName("Deve atualizar loja com sucesso quando usuário for o dono e preservar usuarioId")
void deveAtualizarLojaQuandoUsuarioForDono() {
    Usuario dono = new Usuario();
    dono.setId("user_dono");
    dono.setPapel(Papel.LOJISTA);

    Loja lojaExistente = construirLojaCompleta("loja_1", true);
    lojaExistente.setUsuarioId("user_dono");

    when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(lojaExistente));
    // FALTAVA ISSO:
    when(lojaAccessService.temAcessoALoja(dono, "loja_1")).thenReturn(true);
    when(lojaRepository.save(any(Loja.class))).thenAnswer(invocation -> invocation.getArgument(0));

    LojaDetalhesDTO atualizada = lojaService.atualizarLoja("loja_1", construirDtoCriacao(), dono);

    assertNotNull(atualizada);
    assertEquals("Nova Loja", atualizada.nome());
    verify(lojaRepository).save(argThat(l -> "user_dono".equals(l.getUsuarioId())));
}

    @Test
    @DisplayName("Deve lançar AcessoNegadoException ao tentar atualizar loja de outro usuário")
    void deveLancarAcessoNegadoAoAtualizarLojaDeOutroUsuario() {
        Usuario invasor = new Usuario();
        invasor.setId("user_invasor");
        invasor.setPapel(Papel.LOJISTA);

        Loja lojaExistente = construirLojaCompleta("loja_1", true);
        lojaExistente.setUsuarioId("user_dono");

        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(lojaExistente));

        assertThrows(AcessoNegadoException.class,
                () -> lojaService.atualizarLoja("loja_1", construirDtoCriacao(), invasor));
    }

    @Test
    @DisplayName("Deve permitir que ADMIN atualize qualquer loja")
    void devePermitirAdminAtualizarQualquerLoja() {
        Usuario admin = new Usuario();
        admin.setId("user_admin");
        admin.setPapel(Papel.ADMIN);

        Loja lojaExistente = construirLojaCompleta("loja_1", true);
        lojaExistente.setUsuarioId("user_dono");

        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(lojaExistente));
        when(lojaRepository.save(any(Loja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LojaDetalhesDTO atualizada = lojaService.atualizarLoja("loja_1", construirDtoCriacao(), admin);

        assertNotNull(atualizada);
        verify(lojaRepository).save(argThat(l -> "user_dono".equals(l.getUsuarioId())));
    }

    @Test
    @DisplayName("Deve abrir/fechar a loja rapidamente quando usuário tiver acesso à loja")
    void deveAtualizarAberturaQuandoUsuarioTiverAcesso() {
        Usuario dono = new Usuario();
        dono.setId("user_dono");
        dono.setPapel(Papel.LOJISTA);

        Loja lojaExistente = construirLojaCompleta("loja_1", true);
        lojaExistente.setUsuarioId("user_dono");

        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(lojaExistente));
        when(lojaAccessService.temAcessoALoja(dono, "loja_1")).thenReturn(true);
        when(lojaRepository.save(any(Loja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LojaDetalhesDTO atualizada = lojaService.atualizarAbertura("loja_1", false, dono);

        assertNotNull(atualizada);
        assertFalse(lojaExistente.isAberto());
        verify(lojaRepository).save(argThat(l -> !l.isAberto()));
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException ao tentar abrir/fechar loja sem acesso a ela")
    void deveLancarAcessoNegadoAoAtualizarAberturaSemAcesso() {
        Usuario invasor = new Usuario();
        invasor.setId("user_invasor");
        invasor.setPapel(Papel.LOJISTA);

        Loja lojaExistente = construirLojaCompleta("loja_1", true);
        lojaExistente.setUsuarioId("user_dono");

        when(lojaRepository.findById("loja_1")).thenReturn(Optional.of(lojaExistente));
        when(lojaAccessService.temAcessoALoja(invasor, "loja_1")).thenReturn(false);

        assertThrows(AcessoNegadoException.class,
                () -> lojaService.atualizarAbertura("loja_1", false, invasor));
    }

    private LojaCreateDTO construirDtoCriacao() {
        LojaCreateDTO.DadosOperacionaisDTO dadosOp = new LojaCreateDTO.DadosOperacionaisDTO(new BigDecimal("5.0"), 30, 45, true, false, null);
        LojaCreateDTO.EnderecoDTO endereco = new LojaCreateDTO.EnderecoDTO("Rua X", "123", "Cidade", "SP", "01234-567", "Centro", null);
        LojaCreateDTO.HorariosDTO horarios = new LojaCreateDTO.HorariosDTO("F", "F", "F", "F", "F", "F", "F");
        LojaCreateDTO.FormasPagamentoDTO formasPagto = new LojaCreateDTO.FormasPagamentoDTO(true, true, true, true, false, false);
        return new LojaCreateDTO("Nova Loja", "Desc", "Categoria", "img.jpg", true, dadosOp, endereco, horarios, formasPagto);
    }
}
