package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioAtualizarDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioCreateDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioResponseDTO;
import br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcControllerTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.favorito.FavoritoService favoritoService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.pedido.PedidoService pedidoService;

    @MockitoBean
    private DispositivoPushService dispositivoPushService;

    private static final String USUARIO_LOGADO_ID = "user_123";

    @BeforeEach
    void setUp() {
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId(USUARIO_LOGADO_ID);
        usuarioMock.setEmail("teste@nhac.com");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(usuarioMock, null, Collections.emptyList());

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void naoPermiteRegistrarTokenEmContaDeOutroUsuario() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/outro/push-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token-valido\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dispositivoPushService);
    }

    @Test
    @DisplayName("Deve retornar 200 ao buscar os próprios dados")
    void deveBuscarProprioUsuarioComSucesso() throws Exception {
        UsuarioResponseDTO mockResponse = new UsuarioResponseDTO(
                USUARIO_LOGADO_ID,
                "Matheus Alves",
                "matheus@nhac.com",
                "11999998888",
                null,
                br.com.nhac.backend_nhac.domain.usuario.Papel.CLIENTE,
                null
        );
        when(usuarioService.buscarUsuario(USUARIO_LOGADO_ID)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/usuarios/{id}", USUARIO_LOGADO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USUARIO_LOGADO_ID));
    }

    @Test
    @DisplayName("Deve retornar 403 ao tentar buscar dados de outro usuário")
    void deveRetornar403AoBuscarDadosDeOutroUsuario() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/{id}", "outro_usuario_id"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());

        verify(usuarioService, never()).buscarUsuario(any());
    }

    @Test
    @DisplayName("Deve retornar 201 ao criar um novo usuário com dados válidos")
    void deveCriarUsuarioComSucesso() throws Exception {
        UsuarioCreateDTO dto = new UsuarioCreateDTO(
                "user_novo",
                "Nome Novo",
                "novo@nhac.com",
                "11999998888",
                null,
                "senha123"
        );

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        verify(usuarioService, times(1)).salvarUsuario(any(UsuarioCreateDTO.class));
    }

    @Test
    @DisplayName("Deve retornar 400 ao criar usuário com e-mail inválido")
    void deveRetornar400AoCriarUsuarioComEmailInvalido() throws Exception {
        UsuarioCreateDTO dto = new UsuarioCreateDTO(
                "user_novo",
                "Nome Novo",
                "email-invalido",
                "11999998888",
                null,
                "senha123"
        );

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).salvarUsuario(any());
    }

    @Test
    @DisplayName("Deve retornar 200 e o novo token ao atualizar dados parciais do próprio usuário")
    void deveAtualizarDadosDoProprioUsuario() throws Exception {
        // 6 argumentos: nome, email, telefone, imagemUrl, fcmToken, cpf
        UsuarioAtualizarDTO dados = new UsuarioAtualizarDTO(
                "Nome Atualizado", null, null, null, null, null
        );

        Usuario usuarioMock = new Usuario();
        usuarioMock.setId(USUARIO_LOGADO_ID);
        usuarioMock.setNome("Nome Atualizado");

        when(usuarioRepository.findById(USUARIO_LOGADO_ID)).thenReturn(Optional.of(usuarioMock));
        when(tokenService.gerarToken(any(Usuario.class))).thenReturn("novo_token_jwt_super_seguro");

        mockMvc.perform(put("/api/v1/usuarios/{id}", USUARIO_LOGADO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dados)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("novo_token_jwt_super_seguro"))
                .andExpect(jsonPath("$.nome").value("Nome Atualizado"));

        verify(usuarioService, times(1))
                .atualizarUsuarioParcial(eq(USUARIO_LOGADO_ID), any(UsuarioAtualizarDTO.class));
    }

    @Test
    @DisplayName("Deve retornar 204 ao desativar conta de usuário")
    void deveDesativarUsuarioComSucesso() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/{id}", USUARIO_LOGADO_ID))
                .andExpect(status().isNoContent());

        verify(usuarioService, times(1))
                .desativarUsuario(eq(USUARIO_LOGADO_ID), any(Usuario.class));
    }

    @Test
    @DisplayName("Deve retornar true se usuário segue a loja")
    void deveVerificarSeUsuarioSegueLoja() throws Exception {
        when(favoritoService.usuarioSegueLoja(USUARIO_LOGADO_ID, "loja_1")).thenReturn(true);

        mockMvc.perform(get("/api/v1/usuarios/{usuarioId}/seguindo/{lojaId}",
                        USUARIO_LOGADO_ID, "loja_1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("true"));
    }

    @Test
    @DisplayName("Deve retornar 200 com a lista de pedidos do usuário")
    void deveListarPedidosDoUsuario() throws Exception {
        br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoDTO pedidoMock =
                new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoDTO(
                        "pedido_1",
                        "loja_1",
                        "Loja Teste",
                        new java.math.BigDecimal("50.00"),
                        br.com.nhac.backend_nhac.domain.pedido.StatusPedido.PENDENTE,
                        java.time.Instant.now()
                );

        org.springframework.data.domain.Page<br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoDTO> pagina =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(pedidoMock));

        when(pedidoService.listarMeusPedidos(eq(USUARIO_LOGADO_ID), any())).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/usuarios/{usuarioId}/pedidos", USUARIO_LOGADO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("pedido_1"));
    }

    @Test
    @DisplayName("Deve retornar 200 com as estatísticas do usuário")
    void deveObterEstatisticasDoUsuario() throws Exception {
        br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioEstatisticasDTO statsMock =
                new br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioEstatisticasDTO(15L, 3L, 5L);

        when(usuarioService.obterEstatisticas(USUARIO_LOGADO_ID)).thenReturn(statsMock);

        mockMvc.perform(get("/api/v1/usuarios/{usuarioId}/estatisticas", USUARIO_LOGADO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(15))
                .andExpect(jsonPath("$.lojasFavoritadas").value(3))
                .andExpect(jsonPath("$.cuponsResgatados").value(5));
    }
}
