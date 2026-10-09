package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarLocalizacaoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarStatusDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarVeiculoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarDocumentosDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarDadosBancariosDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.CadastroEntregadorDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntregadorServiceTest {

    @Mock
    private EntregadorRepository entregadorRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private EntregadorService entregadorService;

    private Usuario usuario;

    /**
     * Helper para montar o DTO com todos os campos obrigatórios e opcionais.
     * Mantém os testes legíveis e evita repetir 6 argumentos em cada teste.
     */
    private CadastroEntregadorDTO buildCadastroDTO() {
        return new CadastroEntregadorDTO(
                "12345678900",      // cnh
                "ABC1D23",          // placaVeiculo
                TipoVeiculo.MOTO,   // tipoVeiculo
                "52998224725",      // cpf
                "Preta",            // corVeiculo (opcional)
                "Honda CG 160"      // modeloVeiculo (opcional)
        );
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId("user_123");
        usuario.setNome("Motoboy Carlos");
        usuario.setEmail("carlos@nhac.com");
        usuario.setTelefone("11999998888");
        usuario.setPapel(Papel.CLIENTE);
    }

    @Test
    @DisplayName("Deve cadastrar novo entregador mantendo o papel principal intacto")
    void deveCadastrarEntregadorComSucesso() {
        CadastroEntregadorDTO dto = buildCadastroDTO();

        when(entregadorRepository.existsByUsuarioId(usuario.getId())).thenReturn(false);
        when(entregadorRepository.save(any(Entregador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EntregadorResponseDTO resposta = entregadorService.cadastrar(dto, usuario);

        assertNotNull(resposta);
        assertEquals("ABC1D23", resposta.placaVeiculo());
        assertEquals(StatusOperacional.OFFLINE, resposta.statusOperacional());
        // O papel principal NÃO é mais sobrescrito para ENTREGADOR: quem
        // confirma que esta conta também é entregadora é o registro em
        // tb_entregadores (consultado por AutoridadesFactory).
        assertEquals(Papel.CLIENTE, usuario.getPapel(),
                "o cadastro de entregador não deve mais sobrescrever o papel");
        assertEquals(dto.cpf(), usuario.getCpf());
        verify(usuarioRepository).save(usuario);
        verify(entregadorRepository, times(1)).save(any(Entregador.class));
    }

    @Test
    @DisplayName("Deve recusar cadastro de entregador para contas de loja (LOJISTA/FUNCIONARIO)")
    void deveRecusarCadastroDeEntregadorParaContaDeLoja() {
        CadastroEntregadorDTO dto = buildCadastroDTO();
        when(entregadorRepository.existsByUsuarioId(usuario.getId())).thenReturn(false);

        for (Papel papelDeLoja : List.of(Papel.LOJISTA, Papel.FUNCIONARIO)) {
            usuario.setPapel(papelDeLoja);
            assertThrows(RegraDeNegocioException.class,
                    () -> entregadorService.cadastrar(dto, usuario));
        }

        verify(entregadorRepository, never()).save(any(Entregador.class));
    }

    @Test
    @DisplayName("Deve lancar excecao se usuario ja for cadastrado como entregador")
    void deveLancarExcecaoAoCadastrarEntregadorDuplicado() {
        CadastroEntregadorDTO dto = buildCadastroDTO();
        when(entregadorRepository.existsByUsuarioId(usuario.getId())).thenReturn(true);

        assertThrows(RegraDeNegocioException.class,
                () -> entregadorService.cadastrar(dto, usuario));
        verify(entregadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve alterar status operacional do entregador para ONLINE")
    void deveAtualizarStatusOperacional() {
        Entregador entregador = Entregador.builder()
                .id("ent_1")
                .usuario(usuario)
                .cnh("12345678900")
                .placaVeiculo("ABC1D23")
                .ultimaAtualizacaoLocalizacao(Instant.now())
                .statusOperacional(StatusOperacional.OFFLINE)
                .ativo(true)
                .build();

        when(entregadorRepository.findByUsuarioId(usuario.getId()))
                .thenReturn(Optional.of(entregador));
        when(entregadorRepository.save(any(Entregador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AtualizarStatusDTO dto = new AtualizarStatusDTO(StatusOperacional.ONLINE);
        EntregadorResponseDTO resposta = entregadorService.atualizarStatus(dto, usuario);

        assertEquals(StatusOperacional.ONLINE, resposta.statusOperacional());
    }

    @Test
    void devePersistirEdicaoDeVeiculoEExporCamposAtualizados() {
        Entregador entregador = Entregador.builder().id("ent_1").usuario(usuario)
                .ultimaAtualizacaoLocalizacao(Instant.now())
                .cnh("12345678900").statusOperacional(StatusOperacional.OFFLINE).build();
        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(entregador));
        when(entregadorRepository.save(any(Entregador.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = entregadorService.atualizarVeiculo(
                new AtualizarVeiculoDTO(TipoVeiculo.MOTO, "abc1d23", "Honda CG", "Preta"), usuario);

        assertEquals("ABC1D23", resposta.placaVeiculo());
        assertEquals("Honda CG", resposta.modeloVeiculo());
        assertEquals("Preta", resposta.corVeiculo());
        verify(entregadorRepository).save(entregador);
    }

    @Test
    void naoDeveTrocarVeiculoDuranteEntrega() {
        Entregador entregador = Entregador.builder().id("ent_1").usuario(usuario)
                .statusOperacional(StatusOperacional.EM_ENTREGA).build();
        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(entregador));
        assertThrows(RegraDeNegocioException.class, () -> entregadorService.atualizarVeiculo(
                new AtualizarVeiculoDTO(TipoVeiculo.CARRO, "ABC1234", null, null), usuario));
        verify(entregadorRepository, never()).save(any());
    }

    @Test
    void deveImpedirCpfDeOutraContaSemModificarDocumentos() {
        Entregador entregador = Entregador.builder().id("ent_1").usuario(usuario)
                .cnh("12345678900").build();
        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(entregador));
        when(usuarioRepository.existsByCpfAndIdNot("52998224725", usuario.getId())).thenReturn(true);
        assertThrows(RegraDeNegocioException.class, () -> entregadorService.atualizarDocumentos(
                new AtualizarDocumentosDTO("12345678901", "52998224725"), usuario));
        assertEquals("12345678900", entregador.getCnh());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void devePersistirDocumentosEChavePixNoPerfil() {
        Entregador entregador = Entregador.builder().id("ent_1").usuario(usuario).build();
        when(entregadorRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(entregador));
        when(entregadorRepository.save(any(Entregador.class))).thenAnswer(i -> i.getArgument(0));
        var documentos = entregadorService.atualizarDocumentos(
                new AtualizarDocumentosDTO("12345678901", "52998224725"), usuario);
        assertEquals("52998224725", documentos.cpf());
        assertEquals("12345678901", documentos.cnh());
        verify(usuarioRepository).save(usuario);

        var banco = entregadorService.atualizarDadosBancarios(
                new AtualizarDadosBancariosDTO("EMAIL", " teste@example.com "), usuario);
        assertEquals("EMAIL", banco.tipoChavePix());
        assertEquals("teste@example.com", banco.chavePix());
    }



    @Test
    @DisplayName("Não deve permitir definir EM_ENTREGA manualmente")
    void naoDevePermitirEmEntregaManual() {
        Entregador entregador = Entregador.builder()
                .id("ent_1")
                .usuario(usuario)
                .statusOperacional(StatusOperacional.ONLINE)
                .ativo(true)
                .build();

        when(entregadorRepository.findByUsuarioId(usuario.getId()))
                .thenReturn(Optional.of(entregador));

        assertThrows(RegraDeNegocioException.class,
                () -> entregadorService.atualizarStatus(
                        new AtualizarStatusDTO(StatusOperacional.EM_ENTREGA), usuario));

        verify(entregadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não deve permitir mudança manual enquanto estiver EM_ENTREGA")
    void naoDeveAlterarStatusManualDuranteEntrega() {
        Entregador entregador = Entregador.builder()
                .id("ent_1")
                .usuario(usuario)
                .statusOperacional(StatusOperacional.EM_ENTREGA)
                .ativo(true)
                .build();

        when(entregadorRepository.findByUsuarioId(usuario.getId()))
                .thenReturn(Optional.of(entregador));

        assertThrows(RegraDeNegocioException.class,
                () -> entregadorService.atualizarStatus(
                        new AtualizarStatusDTO(StatusOperacional.ONLINE), usuario));

        verify(entregadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar localizacao GPS do entregador")
    void deveAtualizarLocalizacao() {
        Entregador entregador = Entregador.builder()
                .id("ent_1")
                .usuario(usuario)
                .cnh("12345678900")
                .placaVeiculo("ABC1D23")
                .statusOperacional(StatusOperacional.ONLINE)
                .ativo(true)
                .build();

        when(entregadorRepository.findByUsuarioId(usuario.getId()))
                .thenReturn(Optional.of(entregador));
        when(entregadorRepository.save(any(Entregador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AtualizarLocalizacaoDTO dto = new AtualizarLocalizacaoDTO(-23.55052, -46.63330);
        EntregadorResponseDTO resposta = entregadorService.atualizarLocalizacao(dto, usuario);

        assertEquals(-23.55052, resposta.latitudeAtual());
        assertEquals(-46.63330, resposta.longitudeAtual());
        assertNotNull(resposta.ultimaAtualizacaoLocalizacao());
    }

    @Test
    @DisplayName("Deve filtrar e ordenar entregadores proximos por distancia")
    void deveBuscarEntregadoresProximos() {
        double lojaLat = -23.55052;
        double lojaLng = -46.63330;

        Entregador entPerto = Entregador.builder()
                .id("ent_perto")
                .usuario(usuario)
                .statusOperacional(StatusOperacional.ONLINE)
                .latitudeAtual(-23.56000)
                .longitudeAtual(-46.63330)
                .ultimaAtualizacaoLocalizacao(Instant.now())
                .ativo(true)
                .build();

        Entregador entLonge = Entregador.builder()
                .id("ent_longe")
                .usuario(usuario)
                .statusOperacional(StatusOperacional.ONLINE)
                .latitudeAtual(-23.68000)
                .longitudeAtual(-46.63330)
                .ultimaAtualizacaoLocalizacao(Instant.now())
                .ativo(true)
                .build();

        when(entregadorRepository.findByStatusOperacionalAndAtivoTrue(StatusOperacional.ONLINE))
                .thenReturn(List.of(entLonge, entPerto));

        var resultado = entregadorService.buscarEntregadoresProximos(lojaLat, lojaLng, 5.0);

        assertEquals(1, resultado.size());
        assertEquals("ent_perto", resultado.get(0).entregador().getId());
        assertTrue(resultado.get(0).distanciaKm() < 2.0);
    }

    @Test
    @DisplayName("Deve ignorar localização GPS antiga no despacho")
    void deveIgnorarLocalizacaoAntiga() {
        Entregador antigo = Entregador.builder()
                .id("ent_antigo")
                .usuario(usuario)
                .statusOperacional(StatusOperacional.ONLINE)
                .latitudeAtual(-23.55100)
                .longitudeAtual(-46.63330)
                .ultimaAtualizacaoLocalizacao(Instant.now().minusSeconds(300))
                .ativo(true)
                .build();

        when(entregadorRepository.findByStatusOperacionalAndAtivoTrue(StatusOperacional.ONLINE))
                .thenReturn(List.of(antigo));

        assertTrue(entregadorService.buscarEntregadoresProximos(
                -23.55052, -46.63330, 5.0).isEmpty());
    }

}
