package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.entregador.dto.CadastroEntregadorDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntregadorOnboardingTest {

    @Mock
    private EntregadorRepository entregadorRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private EntregadorService entregadorService;

    private Usuario usuario;

    private CadastroEntregadorDTO buildCadastroDTO() {
        return new CadastroEntregadorDTO(
                "12345678900",
                "BRA2E19",
                TipoVeiculo.MOTO,
                "98765432100",
                "Vermelha",
                "Honda CG 160 Fan"
        );
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId("user_onboarding_1");
        usuario.setNome("Entregador Teste");
        usuario.setEmail("teste.motoca@nhac.com");
        usuario.setTelefone("11987654321");
        usuario.setPapel(Papel.CLIENTE);
    }

    @Test
    @DisplayName("Cenário 1: Usuário autenticado sem entregador deve lançar IdNaoEncontradoException (HTTP 404)")
    void usuarioSemEntregadorDeveLancar404() {
        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.empty());

        assertThrows(IdNaoEncontradoException.class, () -> entregadorService.obterPerfil(usuario));
    }

    @Test
    @DisplayName("Cenário 2: Usuário cadastra como entregador e passa a ter perfil completo")
    void usuarioCadastraEntregadorComSucesso() {
        CadastroEntregadorDTO dto = buildCadastroDTO();

        when(entregadorRepository.existsByUsuarioId(usuario.getId())).thenReturn(false);
        when(entregadorRepository.save(any(Entregador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EntregadorResponseDTO resposta = entregadorService.cadastrar(dto, usuario);

        assertNotNull(resposta);
        assertEquals("BRA2E19", resposta.placaVeiculo());
        assertEquals("Honda CG 160 Fan", resposta.modeloVeiculo());
        assertEquals("Vermelha", resposta.corVeiculo());
        assertEquals(StatusOperacional.OFFLINE, resposta.statusOperacional());
        verify(entregadorRepository, times(1)).save(any(Entregador.class));
    }

    @Test
    @DisplayName("Cenário 3: Tentativa de cadastro duplicado deve lançar RegraDeNegocioException (HTTP 400)")
    void cadastroDuplicadoDeveLancarRegraDeNegocio() {
        CadastroEntregadorDTO dto = buildCadastroDTO();
        when(entregadorRepository.existsByUsuarioId(usuario.getId())).thenReturn(true);

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> entregadorService.cadastrar(dto, usuario));

        assertEquals("Este usuário já possui cadastro como entregador.", ex.getMessage());
        verify(entregadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cenário 4: Usuário com cadastro concluído obtém perfil com sucesso (HTTP 200)")
    void usuarioComCadastroConcluidoObtemPerfil() {
        Entregador entregador = Entregador.builder()
                .id("ent_onboarding_1")
                .usuario(usuario)
                .cnh("12345678900")
                .placaVeiculo("BRA2E19")
                .modeloVeiculo("Honda CG 160 Fan")
                .corVeiculo("Vermelha")
                .tipoVeiculo(TipoVeiculo.MOTO)
                .statusOperacional(StatusOperacional.OFFLINE)
                .ativo(true)
                .build();

        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(entregador));

        EntregadorResponseDTO perfil = entregadorService.obterPerfil(usuario);

        assertNotNull(perfil);
        assertEquals("BRA2E19", perfil.placaVeiculo());
        assertEquals(StatusOperacional.OFFLINE, perfil.statusOperacional());
    }
}
