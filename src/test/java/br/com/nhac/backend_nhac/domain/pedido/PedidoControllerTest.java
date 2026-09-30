package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.pedido.PedidoService;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoUpdateStatusDTO;
import java.time.Instant;
import java.util.List;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResponseDTO;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;

@br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest(controllers = PedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId("user_123");
        usuarioMock.setEmail("teste@nhac.com");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(usuarioMock, null, Collections.emptyList());

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Deve devolver Erro 400 quando o carrinho de itens estiver vazio")
    void deveDevolverErro400QuandoCarrinhoVazio() throws Exception {

        String jsonEstragado = """
                {
                  "lojaId": "loja-001",
                  "formaPagamento": "PIX",
                  "enderecoEntrega": {
                    "rua": "Rua A", "numero": "123", "bairro": "Centro",
                    "cidade": "SP", "estado": "SP", "cep": "01000-000"
                  },
                  "itens": []
                }
                """;

        //noinspection deprecation
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstragado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Deve devolver Erro 400 quando o CEP estiver fora do padrão")
    void deveDevolverErro400QuandoCepInvalido() throws Exception {

        String jsonEstragado = """
                {
                  "lojaId": "loja-001",
                  "formaPagamento": "PIX",
                  "enderecoEntrega": {
                    "rua": "Rua A", "numero": "123", "bairro": "Centro",
                    "cidade": "SP", "estado": "SP",
                    "cep": "123"
                  },
                  "itens": [
                    { "produtoId": "p1", "nome": "Sushi", "quantidade": 1 }
                  ]
                }
                """;

        //noinspection deprecation
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstragado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Deve devolver Erro 400 quando a quantidade de um item for zero ou negativa")
    void deveDevolverErro400QuandoQuantidadeNegativa() throws Exception {

        String jsonEstragado = """
                {
                  "lojaId": "loja-001",
                  "formaPagamento": "PIX",
                  "enderecoEntrega": {
                    "rua": "Rua A", "numero": "123", "bairro": "Centro",
                    "cidade": "SP", "estado": "SP", "cep": "01000-000"
                  },
                  "itens": [
                    { "produtoId": "p1", "nome": "Sushi", "quantidade": 0 }
                  ]
                }
                """;

        //noinspection deprecation
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstragado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Deve devolver 201 e o ID do pedido quando os dados forem válidos")
    void deveCriarPedidoComSucesso() throws Exception {
        String jsonValido = """
                {
                  "lojaId": "loja-001",
                  "formaPagamento": "PIX",
                  "enderecoEntrega": {
                    "rua": "Rua A", "numero": "123", "bairro": "Centro",
                    "cidade": "SP", "estado": "SP", "cep": "01000-000"
                  },
                  "itens": [
                    { "produtoId": "p1", "nome": "Sushi", "quantidade": 1 }
                  ]
                }
                """;

        br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO dto = new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO("pedido_gerado_001", null, null, null);
        br.com.nhac.backend_nhac.domain.pedido.dto.ResultadoCriacaoPedido resultado = new br.com.nhac.backend_nhac.domain.pedido.dto.ResultadoCriacaoPedido(dto, false);
        when(pedidoService.finalizarPedido(any(PedidoCreateDTO.class), any(Usuario.class), any())).thenReturn(resultado);

        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonValido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pedidoId").value("pedido_gerado_001"));
    }

    @Test
    @DisplayName("Deve devolver 200 quando idempotency-key já existir (replay de pedido)")
    void deveRetornar200QuandoIdempotencyKeyForReplay() throws Exception {
        String jsonValido = """
                {
                  "lojaId": "loja-001",
                  "formaPagamento": "PIX",
                  "enderecoEntrega": {
                    "rua": "Rua A", "numero": "123", "bairro": "Centro",
                    "cidade": "SP", "estado": "SP", "cep": "01000-000"
                  },
                  "itens": [
                    { "produtoId": "p1", "nome": "Sushi", "quantidade": 1 }
                  ]
                }
                """;

        br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO dto = new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO("pedido_existente_001", null, null, null);
        br.com.nhac.backend_nhac.domain.pedido.dto.ResultadoCriacaoPedido resultado = new br.com.nhac.backend_nhac.domain.pedido.dto.ResultadoCriacaoPedido(dto, true);
        when(pedidoService.finalizarPedido(any(PedidoCreateDTO.class), any(Usuario.class), any())).thenReturn(resultado);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Idempotency-Key", "idemp-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value("pedido_existente_001"));
    }

    @Test
    @DisplayName("Deve devolver 200 e o PedidoResponseDTO quando o usuário logado for dono do pedido")
    void deveRetornarPedidoComStatus200QuandoExistirEDonoEstiverLogado() throws Exception {
        PedidoResponseDTO mockResponse = new PedidoResponseDTO(
                "pedido_123", "user_123", "loja_001", "Loja Teste", new BigDecimal("100.00"), new BigDecimal("5.00"),
                "PIX", null, null, StatusPedido.PENDENTE, null, null, Collections.emptyList(), BigDecimal.ZERO, null,
                null, null, false
        );

        when(pedidoService.buscarPedido("pedido_123", "user_123")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/pedidos/pedido_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pedido_123"))
                .andExpect(jsonPath("$.lojaId").value("loja_001"));
    }

    @Test
    @DisplayName("Deve devolver 403 quando o usuário logado não for o dono do pedido")
    void deveRetornar403QuandoUsuarioLogadoNaoForDonoDoPedido() throws Exception {
        when(pedidoService.buscarPedido("pedido_123", "user_123"))
                .thenThrow(new br.com.nhac.backend_nhac.exceptions.AcessoNegadoException("Acesso negado"));

        mockMvc.perform(get("/api/v1/pedidos/pedido_123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve devolver 404 quando o pedido não for encontrado")
    void deveRetornar404QuandoPedidoNaoForEncontrado() throws Exception {
        when(pedidoService.buscarPedido("pedido_inexistente", "user_123"))
                .thenThrow(new br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException("Pedido não encontrado"));

        mockMvc.perform(get("/api/v1/pedidos/pedido_inexistente"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve devolver 200 e a página de pedidos do usuário logado")
    void deveRetornarStatus200EListaPaginadaDePedidosDoUsuarioLogado() throws Exception {
        PedidoResumoDTO pedidoMock = new PedidoResumoDTO(
                "pedido_123", "loja_001", "Loja Teste", new BigDecimal("100.00"), StatusPedido.PENDENTE, Instant.now()
        );
        Page<PedidoResumoDTO> pageMock = new PageImpl<>(List.of(pedidoMock));

        when(pedidoService.listarMeusPedidos(anyString(), any())).thenReturn(pageMock);

        mockMvc.perform(get("/api/v1/pedidos?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("pedido_123"))
                .andExpect(jsonPath("$.content[0].lojaId").value("loja_001"));
    }

    @Test
    @DisplayName("Deve retornar 204 ao atualizar status com sucesso")
    void deveRetornar204AoAtualizarStatusComSucesso() throws Exception {
        String jsonBody = "{\"status\": \"PREPARANDO\"}";

        doNothing().when(pedidoService).atualizarStatus(anyString(), any(StatusPedido.class), any(Usuario.class));

        mockMvc.perform(patch("/api/v1/pedidos/pedido_123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deve retornar 400 ao enviar status nulo")
    void deveRetornar400AoEnviarStatusNulo() throws Exception {
        String jsonBody = "{\"status\": null}";

        mockMvc.perform(patch("/api/v1/pedidos/pedido_123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar 204 ao cancelar pedido com sucesso")
    void deveRetornar204AoCancelarPedidoComSucesso() throws Exception {
        doNothing().when(pedidoService).cancelarPedido(anyString(), anyString());

        mockMvc.perform(patch("/api/v1/pedidos/pedido_123/cancelar"))
                .andExpect(status().isNoContent());
    }
}