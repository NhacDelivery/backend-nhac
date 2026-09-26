package br.com.nhac.backend_nhac.domain.loja;

import br.com.nhac.backend_nhac.domain.loja.dto.LojaCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaDetalhesDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcControllerTest(controllers = LojaController.class)
@AutoConfigureMockMvc(addFilters = false)
class LojaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LojaService lojaService;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    private Usuario usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Usuario();
        usuarioLogado.setId("user_123");
        usuarioLogado.setEmail("teste@nhac.com");
        usuarioLogado.setPapel(Papel.CLIENTE);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(usuarioLogado, null, usuarioLogado.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve retornar 200 com a página de lojas abertas")
    void deveListarLojasComSucesso() throws Exception {
        LojaResumoDTO.DadosOperacionaisDTO dadosOp =
                new LojaResumoDTO.DadosOperacionaisDTO(4.8f, new BigDecimal("5.99"), 30, 45, 150, true, false, null);
        LojaResumoDTO.FormasPagamentoDTO formasPagto = new LojaResumoDTO.FormasPagamentoDTO(true, true, true, true, false, false);
        LojaResumoDTO resumo = new LojaResumoDTO("loja_1", "Sushi Ken", "Descrição", "Japonesa", "url", dadosOp, formasPagto);
        Page<LojaResumoDTO> pagina = new PageImpl<>(List.of(resumo), PageRequest.of(0, 10), 1);

        when(lojaService.obterLojasPaginadas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyInt(), anyInt())).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/lojas").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("loja_1"))
                .andExpect(jsonPath("$.content[0].nome").value("Sushi Ken"));
    }

    @Test
    @DisplayName("Deve retornar 200 com os detalhes de uma loja existente")
    void deveRetornarDetalhesDaLojaComSucesso() throws Exception {
        LojaDetalhesDTO.DadosOperacionaisDTO dadosOp =
                new LojaDetalhesDTO.DadosOperacionaisDTO(4.8f, new BigDecimal("5.99"), 30, 45, 150, true, false, null);
        LojaDetalhesDTO detalhes = getLojaDetalhesDTO(dadosOp);

        when(lojaService.obterLojaId("loja_1")).thenReturn(detalhes);

        mockMvc.perform(get("/api/v1/lojas/loja_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("loja_1"))
                .andExpect(jsonPath("$.endereco.rua").value("Rua das Flores"));
    }

    private static @NonNull LojaDetalhesDTO getLojaDetalhesDTO(LojaDetalhesDTO.DadosOperacionaisDTO dadosOp) {
        LojaDetalhesDTO.EnderecoDTO endereco =
                new LojaDetalhesDTO.EnderecoDTO("Rua das Flores", "123", "São Paulo", "SP", "01000-000", "Centro", "Sala 42");
        LojaDetalhesDTO.HorariosDTO horarios = new LojaDetalhesDTO.HorariosDTO(
                "18:00-23:00", "Fechado", "11:00-23:00", "11:00-23:00", "11:00-23:00", "11:00-23:59", "11:00-23:59");
        LojaDetalhesDTO.FormasPagamentoDTO formasPagto = new LojaDetalhesDTO.FormasPagamentoDTO(true, true, true, true, false, false);
        LojaDetalhesDTO detalhes = new LojaDetalhesDTO(
                "loja_1", "Sushi Ken", "Descrição completa", "Japonesa", "url", true, dadosOp, endereco, horarios, formasPagto, null, null);
        return detalhes;
    }

    @Test
    @DisplayName("Deve retornar 404 quando a loja não for encontrada")
    void deveRetornar404QuandoLojaNaoEncontrada() throws Exception {
        when(lojaService.obterLojaId("loja_fantasma"))
                .thenThrow(new IdNaoEncontradoException("A loja com o id: loja_fantasma não foi encontrada."));

        mockMvc.perform(get("/api/v1/lojas/{id}", "loja_fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("A loja com o id: loja_fantasma não foi encontrada."));
    }

    @Test
    @DisplayName("Deve criar loja autenticado usando o usuário do token, ignorando usuarioId no corpo")
    void deveCriarLojaComUsuarioAutenticadoIgnorandoUsuarioIdDoCorpo() throws Exception {
        LojaResumoDTO.DadosOperacionaisDTO dadosOp =
                new LojaResumoDTO.DadosOperacionaisDTO(0.0f, new BigDecimal("5.99"), 30, 45, 0, true, false, null);
        LojaResumoDTO.FormasPagamentoDTO formasPagto = new LojaResumoDTO.FormasPagamentoDTO(true, true, true, true, false, false);
        LojaResumoDTO resumo = new LojaResumoDTO("loja_0001", "Mercado Central", "Orgânicos", "Restaurantes", "url", dadosOp, formasPagto);

        when(lojaService.criarLoja(any(LojaCreateDTO.class), eq(usuarioLogado))).thenReturn(resumo);

        String json = """
                {
                  "nome": "Mercado Central",
                  "descricao": "Orgânicos",
                  "categoria": "Restaurantes",
                  "imagemUrl": "https://example.com/banner.jpg",
                  "isAberto": true,
                  "usuarioId": "usuario-forjado",
                  "dadosOperacionais": {
                    "taxaEntregaBase": 5.99,
                    "tempoEntregaMin": 30,
                    "tempoEntregaMax": 45,
                    "entregaPropria": true,
                    "retiradaNoLocal": false
                  },
                  "endereco": {
                    "rua": "Avenida Paulista",
                    "numero": "1578",
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "cep": "01310-200",
                    "bairro": "Bela Vista",
                    "complemento": "Sala 42"
                  },
                  "horarios": {
                    "domingo": "18:00-23:00",
                    "segunda": "Fechado",
                    "terca": "11:00-23:00",
                    "quarta": "11:00-23:00",
                    "quinta": "11:00-23:00",
                    "sexta": "11:00-23:59",
                    "sabado": "11:00-23:59"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/lojas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("loja_0001"))
                .andExpect(jsonPath("$.nome").value("Mercado Central"));

        verify(lojaService).criarLoja(any(LojaCreateDTO.class), eq(usuarioLogado));
    }

    @Test
    @DisplayName("GET /minha-loja deve retornar 200 com os dados da loja do usuário logado")
    void deveRetornarMinhaLojaComSucesso() throws Exception {
        LojaDetalhesDTO.DadosOperacionaisDTO dadosOp =
                new LojaDetalhesDTO.DadosOperacionaisDTO(4.8f, new BigDecimal("5.99"), 30, 45, 150, true, false, null);
        LojaDetalhesDTO detalhes = getLojaDetalhesDTO(dadosOp);

        when(lojaService.obterMinhaLoja(eq(usuarioLogado))).thenReturn(detalhes);

        mockMvc.perform(get("/api/v1/lojas/minha-loja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("loja_1"))
                .andExpect(jsonPath("$.nome").value("Sushi Ken"));
    }

    @Test
    @DisplayName("GET /minha-loja deve retornar 404 quando o usuário não tiver loja cadastrada")
    void deveRetornar404MinhaLojaQuandoUsuarioNaoTiverLoja() throws Exception {
        when(lojaService.obterMinhaLoja(eq(usuarioLogado)))
                .thenThrow(new br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException());

        mockMvc.perform(get("/api/v1/lojas/minha-loja"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /lojas/{id} deve retornar 200 ao atualizar loja com sucesso pelo dono")
    void deveAtualizarLojaComSucesso() throws Exception {
        LojaDetalhesDTO.DadosOperacionaisDTO dadosOp =
                new LojaDetalhesDTO.DadosOperacionaisDTO(4.8f, new BigDecimal("5.99"), 30, 45, 150, true, false, null);
        LojaDetalhesDTO detalhes = getLojaDetalhesDTO(dadosOp);

        when(lojaService.atualizarLoja(eq("loja_1"), any(LojaCreateDTO.class), eq(usuarioLogado))).thenReturn(detalhes);

        String json = """
                {
                  "nome": "Sushi Ken Atualizado",
                  "descricao": "Descrição",
                  "categoria": "Japonesa",
                  "imagemUrl": "https://example.com/banner.jpg",
                  "isAberto": true,
                  "dadosOperacionais": {
                    "taxaEntregaBase": 5.99,
                    "tempoEntregaMin": 30,
                    "tempoEntregaMax": 45,
                    "entregaPropria": true,
                    "retiradaNoLocal": false
                  },
                  "endereco": {
                    "rua": "Avenida Paulista",
                    "numero": "1578",
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "cep": "01310-200",
                    "bairro": "Bela Vista",
                    "complemento": "Sala 42"
                  },
                  "horarios": {
                    "domingo": "18:00-23:00",
                    "segunda": "Fechado",
                    "terca": "11:00-23:00",
                    "quarta": "11:00-23:00",
                    "quinta": "11:00-23:00",
                    "sexta": "11:00-23:59",
                    "sabado": "11:00-23:59"
                  }
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/lojas/loja_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("loja_1"));
    }

    @Test
    @DisplayName("PUT /lojas/{id} deve retornar 403 quando o usuário não for o dono")
    void deveRetornar403AoAtualizarLojaDeOutroUsuario() throws Exception {
        when(lojaService.atualizarLoja(eq("loja_1"), any(LojaCreateDTO.class), eq(usuarioLogado)))
                .thenThrow(new br.com.nhac.backend_nhac.exceptions.AcessoNegadoException("Acesso negado"));

        String json = """
                {
                  "nome": "Tentativa Invasora",
                  "descricao": "Desc",
                  "categoria": "Japonesa",
                  "imagemUrl": "https://example.com/banner.jpg",
                  "isAberto": true,
                  "dadosOperacionais": {
                    "taxaEntregaBase": 5.99,
                    "tempoEntregaMin": 30,
                    "tempoEntregaMax": 45,
                    "entregaPropria": true,
                    "retiradaNoLocal": false
                  },
                  "endereco": {
                    "rua": "Avenida Paulista",
                    "numero": "1578",
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "cep": "01310-200",
                    "bairro": "Bela Vista",
                    "complemento": "Sala 42"
                  },
                  "horarios": {
                    "domingo": "18:00-23:00",
                    "segunda": "Fechado",
                    "terca": "11:00-23:00",
                    "quarta": "11:00-23:00",
                    "quinta": "11:00-23:00",
                    "sexta": "11:00-23:59",
                    "sabado": "11:00-23:59"
                  }
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/lojas/loja_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }
}
