package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.usuario.dto.EnderecoUsuarioDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioAtualizarDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioCreateDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioResponseDTO;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.CredenciaisInvalidasException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private br.com.nhac.backend_nhac.domain.cupom.CupomRepository cupomRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EnderecoUsuarioRepository enderecoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private br.com.nhac.backend_nhac.domain.pedido.PedidoRepository pedidoRepository;
    @Mock private br.com.nhac.backend_nhac.domain.favorito.FavoritoRepository favoritoRepository;

    @InjectMocks private UsuarioService usuarioService;

    @Test
    void estatisticasContamCuponsRecebidosMesmoSemPedido() {
        when(usuarioRepository.existsById("cliente")).thenReturn(true);
        when(cupomRepository.countByUsuarioId("cliente")).thenReturn(2L);
        var stats = usuarioService.obterEstatisticas("cliente");
        assertEquals(2L, stats.cuponsResgatados());
        assertEquals(0L, stats.totalPedidos());
        verify(pedidoRepository, never()).countByUsuarioIdAndCupomIdIsNotNull(any());
    }

    @Test
    void removerPadraoPromoveOutroEnderecoDaMesmaConta() {
        Usuario usuario = usuarioPadrao("cliente");
        EnderecoUsuario removido = new EnderecoUsuario();
        removido.setId("a"); removido.setUsuario(usuario); removido.setPadrao(true);
        EnderecoUsuario proximo = new EnderecoUsuario();
        proximo.setId("b"); proximo.setUsuario(usuario);
        when(enderecoRepository.findById("a")).thenReturn(Optional.of(removido));
        when(enderecoRepository.findByUsuarioId("cliente")).thenReturn(List.of(proximo));
        usuarioService.removerEndereco("cliente", "a");
        assertTrue(proximo.isPadrao());
        verify(usuarioRepository).findLockedById("cliente");
        verify(enderecoRepository).delete(removido);
        verify(enderecoRepository).save(proximo);
    }

    @Test
    void removerUltimoPadraoNaoInventaEndereco() {
        EnderecoUsuario removido = new EnderecoUsuario();
        removido.setId("a"); removido.setUsuario(usuarioPadrao("cliente")); removido.setPadrao(true);
        when(enderecoRepository.findById("a")).thenReturn(Optional.of(removido));
        when(enderecoRepository.findByUsuarioId("cliente")).thenReturn(List.of());
        usuarioService.removerEndereco("cliente", "a");
        verify(enderecoRepository, never()).save(any());
    }

    private Usuario usuarioPadrao(String id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Matheus Alves");
        usuario.setEmail("matheus@nhac.com");
        usuario.setTelefone("11999998888");
        return usuario;
    }

    @Test
    @DisplayName("Deve retornar os dados públicos do usuário quando encontrado")
    void deveBuscarUsuarioComSucesso() {
        Usuario usuario = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = usuarioService.buscarUsuario("user_1");

        assertEquals("user_1", resultado.id());
        assertEquals("Matheus Alves", resultado.nome());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao buscar usuário inexistente")
    void deveLancarExcecaoQuandoBuscarUsuarioInexistente() {
        when(usuarioRepository.findById("fantasma")).thenReturn(Optional.empty());

        assertThrows(IdNaoEncontradoException.class,
                () -> usuarioService.buscarUsuario("fantasma"));
    }

    @Test
    @DisplayName("Deve encriptar a senha e salvar o usuário quando a senha for informada")
    void deveSalvarUsuarioComSenhaEncriptada() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO(
                "user_1", "Matheus Alves", "matheus@nhac.com",
                "11999998888", null, "senhaSegura123"
        );

        when(passwordEncoder.encode("senhaSegura123")).thenReturn("hash_gerado");

        usuarioService.salvarUsuario(dto);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("hash_gerado", captor.getValue().getSenha());
    }

    @Test
    @DisplayName("Não deve encriptar senha quando ela for nula ou em branco")
    void deveSalvarUsuarioSemEncriptarQuandoSenhaAusente() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO(
                "user_1", "Matheus Alves", "matheus@nhac.com",
                "11999998888", null, "  "
        );

        usuarioService.salvarUsuario(dto);

        verify(passwordEncoder, never()).encode(any());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("  ", captor.getValue().getSenha());
    }

    @Test
    @DisplayName("Deve atualizar apenas os campos informados no DTO")
    void deveAtualizarUsuarioParcialmente() {
        Usuario usuario = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuario));

        UsuarioAtualizarDTO dados = new UsuarioAtualizarDTO(
                "Novo Nome", null, "11888887777", null, null, null
        );

        usuarioService.atualizarUsuarioParcial("user_1", dados);

        assertEquals("Novo Nome", usuario.getNome());
        assertEquals("11888887777", usuario.getTelefone());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Deve desativar o próprio usuário com sucesso")
    void deveDesativarProprioUsuarioComSucesso() {
        Usuario usuarioMock = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioMock));

        usuarioService.desativarUsuario("user_1", usuarioMock);

        assertFalse(usuarioMock.isAtivo());
        verify(usuarioRepository, times(1)).save(usuarioMock);
    }

    @Test
    @DisplayName("Deve desativar outro usuário se for ADMIN")
    void deveDesativarOutroUsuarioSeAdmin() {
        Usuario usuarioMock = usuarioPadrao("user_1");
        Usuario adminLogado = new Usuario();
        adminLogado.setId("admin_123");
        adminLogado.setPapel(Papel.ADMIN);

        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioMock));

        usuarioService.desativarUsuario("user_1", adminLogado);

        assertFalse(usuarioMock.isAtivo());
        verify(usuarioRepository, times(1)).save(usuarioMock);
    }

    @Test
    @DisplayName("Não deve desativar outro usuário se for CLIENTE")
    void naoDeveDesativarOutroUsuarioSeCliente() {
        Usuario clienteLogado = new Usuario();
        clienteLogado.setId("cliente_123");
        clienteLogado.setPapel(Papel.CLIENTE);

        assertThrows(AcessoNegadoException.class, () -> {
            usuarioService.desativarUsuario("user_1", clienteLogado);
        });

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao atualizar usuário inexistente")
    void deveLancarExcecaoAoAtualizarUsuarioInexistente() {
        when(usuarioRepository.findById("fantasma")).thenReturn(Optional.empty());

        UsuarioAtualizarDTO dados = new UsuarioAtualizarDTO(
                "X", null, null, null, null, null
        );

        assertThrows(IdNaoEncontradoException.class,
                () -> usuarioService.atualizarUsuarioParcial("fantasma", dados));
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao trocar para um e-mail que já pertence a outra conta")
    void deveLancarErroQuandoNovoEmailJaEstiverEmUso() {
        Usuario usuario = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmailIgnoreCase("outro@nhac.com"))
                .thenReturn(Optional.of(usuarioPadrao("user_2")));

        UsuarioAtualizarDTO dados = new UsuarioAtualizarDTO(
                null, "outro@nhac.com", null, null, null, null
        );

        assertThrows(RegraDeNegocioException.class,
                () -> usuarioService.atualizarUsuarioParcial("user_1", dados));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não deve checar duplicidade quando o e-mail enviado for o mesmo que o usuário já tem")
    void naoDeveChecarDuplicidadeQuandoEmailNaoMudou() {
        Usuario usuario = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuario));

        UsuarioAtualizarDTO dados = new UsuarioAtualizarDTO(
                null, "matheus@nhac.com", null, null, null, null
        );

        usuarioService.atualizarUsuarioParcial("user_1", dados);

        verify(usuarioRepository, never()).findByEmailIgnoreCase(any());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Deve listar os endereços de um usuário")
    void deveListarEnderecosDoUsuario() {
        EnderecoUsuario endereco = new EnderecoUsuario(
                "end_1", usuarioPadrao("user_1"),
                "Rua A", "123", "Centro", "SP", "SP", "01000-000", null, true
        );

        when(enderecoRepository.findByUsuarioId("user_1")).thenReturn(List.of(endereco));

        List<EnderecoUsuarioDTO> resultado = usuarioService.listarEnderecos("user_1");

        assertEquals(1, resultado.size());
        assertEquals("end_1", resultado.get(0).id());
    }

    @Test
    @DisplayName("Deve adicionar um novo endereço ao usuário existente")
    void deveAdicionarEnderecoComSucesso() {
        Usuario usuario = usuarioPadrao("user_1");
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuario));

        EnderecoUsuarioDTO dto = new EnderecoUsuarioDTO(
                null, "Rua A", "123", "Centro",
                "SP", "SP", "01000-000", null, true
        );

        usuarioService.adicionarEndereco("user_1", dto);

        verify(enderecoRepository, times(1)).save(any(EnderecoUsuario.class));
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao adicionar endereço para usuário inexistente")
    void deveLancarExcecaoAoAdicionarEnderecoParaUsuarioInexistente() {
        when(usuarioRepository.findById("fantasma")).thenReturn(Optional.empty());

        EnderecoUsuarioDTO dto = new EnderecoUsuarioDTO(
                null, "Rua A", "123", "Centro",
                "SP", "SP", "01000-000", null, true
        );

        assertThrows(IdNaoEncontradoException.class,
                () -> usuarioService.adicionarEndereco("fantasma", dto));

        verify(enderecoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar um endereço pertencente ao usuário")
    void deveAtualizarEnderecoComSucesso() {
        Usuario usuario = usuarioPadrao("user_1");
        EnderecoUsuario endereco = new EnderecoUsuario(
                "end_1", usuario,
                "Rua Antiga", "1", "Bairro", "SP", "SP", "01000-000", null, false
        );

        when(enderecoRepository.findById("end_1")).thenReturn(Optional.of(endereco));

        EnderecoUsuarioDTO dto = new EnderecoUsuarioDTO(
                "end_1", "Rua Nova", "2", "Bairro Novo",
                "Campinas", "SP", "13000-000", "Apto 1", true
        );

        usuarioService.atualizarEndereco("user_1", "end_1", dto);

        assertEquals("Rua Nova", endereco.getRua());
        assertEquals("Campinas", endereco.getCidade());
        assertTrue(endereco.isPadrao());
        verify(enderecoRepository).save(endereco);
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao atualizar endereço inexistente")
    void deveLancarExcecaoAoAtualizarEnderecoInexistente() {
        when(enderecoRepository.findById("fantasma")).thenReturn(Optional.empty());

        EnderecoUsuarioDTO dto = new EnderecoUsuarioDTO(
                "fantasma", "Rua Nova", "2", "Bairro Novo",
                "Campinas", "SP", "13000-000", null, true
        );

        assertThrows(IdNaoEncontradoException.class,
                () -> usuarioService.atualizarEndereco("user_1", "fantasma", dto));
    }

    @Test
    @DisplayName("Deve lançar CredenciaisInvalidasException ao atualizar endereço de outro usuário")
    void deveLancarExcecaoAoAtualizarEnderecoDeOutroUsuario() {
        Usuario donoReal = usuarioPadrao("dono_real");
        EnderecoUsuario endereco = new EnderecoUsuario(
                "end_1", donoReal,
                "Rua Antiga", "1", "Bairro", "SP", "SP", "01000-000", null, false
        );

        when(enderecoRepository.findById("end_1")).thenReturn(Optional.of(endereco));

        EnderecoUsuarioDTO dto = new EnderecoUsuarioDTO(
                "end_1", "Rua Nova", "2", "Bairro Novo",
                "Campinas", "SP", "13000-000", null, true
        );

        assertThrows(CredenciaisInvalidasException.class,
                () -> usuarioService.atualizarEndereco("invasor_id", "end_1", dto));

        verify(enderecoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve remover um endereço pertencente ao usuário")
    void deveRemoverEnderecoComSucesso() {
        Usuario usuario = usuarioPadrao("user_1");
        EnderecoUsuario endereco = new EnderecoUsuario(
                "end_1", usuario,
                "Rua A", "1", "Bairro", "SP", "SP", "01000-000", null, false
        );

        when(enderecoRepository.findById("end_1")).thenReturn(Optional.of(endereco));

        usuarioService.removerEndereco("user_1", "end_1");

        verify(enderecoRepository, times(1)).delete(endereco);
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao remover endereço inexistente")
    void deveLancarExcecaoAoRemoverEnderecoInexistente() {
        when(enderecoRepository.findById("fantasma")).thenReturn(Optional.empty());

        assertThrows(IdNaoEncontradoException.class,
                () -> usuarioService.removerEndereco("user_1", "fantasma"));
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException ao remover endereço de outro usuário")
    void deveLancarExcecaoAoRemoverEnderecoDeOutroUsuario() {
        Usuario donoReal = usuarioPadrao("dono_real");
        EnderecoUsuario endereco = new EnderecoUsuario(
                "end_1", donoReal,
                "Rua A", "1", "Bairro", "SP", "SP", "01000-000", null, false
        );

        when(enderecoRepository.findById("end_1")).thenReturn(Optional.of(endereco));

        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.removerEndereco("invasor_id", "end_1"));

        verify(enderecoRepository, never()).delete(any(EnderecoUsuario.class));
    }

    @Test
    @DisplayName("Deve obter as estatísticas de um usuário")
    void deveObterEstatisticas() {
        when(usuarioRepository.existsById("usu_1")).thenReturn(true);
        when(pedidoRepository.countByUsuarioId("usu_1")).thenReturn(15L);
        when(favoritoRepository.countByUsuarioId("usu_1")).thenReturn(3L);
        when(cupomRepository.countByUsuarioId("usu_1")).thenReturn(5L);

        br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioEstatisticasDTO stats =
                usuarioService.obterEstatisticas("usu_1");

        assertEquals(15L, stats.totalPedidos());
        assertEquals(3L, stats.lojasFavoritadas());
        assertEquals(5L, stats.cuponsResgatados());
    }
}