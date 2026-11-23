package br.com.nhac.backend_nhac.domain.favorito;

import br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoCreateDTO;
import br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.favorito.FavoritoService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest(controllers = FavoritoController.class)
@AutoConfigureMockMvc(addFilters = false)
class FavoritoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FavoritoService favoritoService;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

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
    @DisplayName("Deve favoritar uma loja com sucesso")
    void deveFavoritarLoja() throws Exception {
        FavoritoCreateDTO dto = new FavoritoCreateDTO("loja_1");
        FavoritoResponseDTO response = new FavoritoResponseDTO("fav_1", "loja_1", "Loja Teste", "imagem.png", Instant.now(), "Japonesa", "capa.jpg", 20, 40, 4.7f, 12);

        when(favoritoService.favoritar(eq(USUARIO_LOGADO_ID), any(FavoritoCreateDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/favoritos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("fav_1"));
    }

    @Test
    @DisplayName("Deve listar favoritos do usuario logado")
    void deveListarFavoritos() throws Exception {
        FavoritoResponseDTO response = new FavoritoResponseDTO("fav_1", "loja_1", "Loja Teste", "imagem.png", Instant.now(), "Japonesa", "capa.jpg", 20, 40, 4.7f, 12);
        org.springframework.data.domain.Page<FavoritoResponseDTO> pagina = 
                new org.springframework.data.domain.PageImpl<>(List.of(response));

        when(favoritoService.listarFavoritos(eq(USUARIO_LOGADO_ID), any())).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/favoritos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("fav_1"));
    }

    @Test
    @DisplayName("Deve remover favorito")
    void deveRemoverFavorito() throws Exception {
        mockMvc.perform(delete("/api/v1/favoritos/{lojaId}", "loja_1"))
                .andExpect(status().isNoContent());

        verify(favoritoService, times(1)).removerFavorito(USUARIO_LOGADO_ID, "loja_1");
    }

    @Test
    @DisplayName("Deve retornar a contagem de seguidores de uma loja")
    void deveRetornarContagemSeguidores() throws Exception {
        when(favoritoService.contarSeguidores("loja_1")).thenReturn(100L);

        mockMvc.perform(get("/api/v1/favoritos/lojas/{lojaId}/contagem", "loja_1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("100"));
    }
}
