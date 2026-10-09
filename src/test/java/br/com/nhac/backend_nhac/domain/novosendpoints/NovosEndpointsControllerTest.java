package br.com.nhac.backend_nhac.domain.novosendpoints;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nhac.backend_nhac.domain.chat.ChatController;
import br.com.nhac.backend_nhac.domain.chat.ChatService;
import br.com.nhac.backend_nhac.domain.chat.ConversaClienteController;
import br.com.nhac.backend_nhac.domain.chat.ParticipanteTipo;
import br.com.nhac.backend_nhac.domain.chat.RemetenteTipo;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaResumoDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.financeiro.FinanceiroController;
import br.com.nhac.backend_nhac.domain.financeiro.FinanceiroService;
import br.com.nhac.backend_nhac.domain.financeiro.PeriodoFinanceiro;
import br.com.nhac.backend_nhac.domain.financeiro.dto.FinanceiroDTOs.FinanceiroDTO;
import br.com.nhac.backend_nhac.domain.financeiro.dto.FinanceiroDTOs.ResumoFinanceiroDTO;
import br.com.nhac.backend_nhac.domain.painel.PainelController;
import br.com.nhac.backend_nhac.domain.painel.PainelService;
import br.com.nhac.backend_nhac.domain.painel.dto.FaturamentoDiaDTO;
import br.com.nhac.backend_nhac.domain.painel.dto.PainelResumoDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoLojistaDTO;
import br.com.nhac.backend_nhac.domain.usuario.FuncionarioController;
import br.com.nhac.backend_nhac.domain.usuario.FuncionarioService;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.dto.FuncionarioResponseDTO;

@br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest(controllers = {FuncionarioController.class, PainelController.class, FinanceiroController.class,
        ChatController.class, ConversaClienteController.class})
@AutoConfigureMockMvc(addFilters = false)
class NovosEndpointsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuncionarioService funcionarioService;

    @MockitoBean
    private PainelService painelService;

    @MockitoBean
    private FinanceiroService financeiroService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private br.com.nhac.backend_nhac.infra.security.TokenService tokenService;

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    private Usuario usuario;

@BeforeEach
void configurarAutenticacao() {
    usuario = new Usuario();
    usuario.setId("lojista_1");
    usuario.setEmail("lojista@nhac.com");
    usuario.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.CLIENTE);   // ← adiciona
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
}

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clienteEnviaJsonEPublicaNoTopico() throws Exception {
        MensagemDTO mensagem = new MensagemDTO("msg_1", "conv_1", RemetenteTipo.CLIENTE,
                usuario.getId(), "Olá", Instant.now());
        when(chatService.enviarMensagem("conv_1", usuario, "Olá", null)).thenReturn(mensagem);
        mockMvc.perform(post("/api/v1/conversas/conv_1/mensagens").with(csrf())
                        .contentType("application/json").content("{\"conteudo\":\"Olá\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("msg_1"));
        verify(chatService).validarConversaDoCliente("conv_1", usuario);
        verify(messagingTemplate).convertAndSend("/topic/conversas/conv_1", mensagem);
    }

    @Test
    void rejeitaMensagemVaziaAntesDeEnviar() throws Exception {
        mockMvc.perform(post("/api/v1/conversas/conv_1/mensagens").with(csrf())
                        .contentType("application/json").content("{\"conteudo\":\"   \"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate, chatService);
    }

    @Test
    void deveListarFuncionarios() throws Exception {
        FuncionarioResponseDTO funcionario = new FuncionarioResponseDTO(
                "func_1", "Ana", "ana@nhac.com", "11999999999", "Atendente", null, true, Instant.now());
        when(funcionarioService.listar(eq(usuario), any())).thenReturn(
                new PageImpl<>(List.of(funcionario), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/lojista/funcionarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("func_1"))
                .andExpect(jsonPath("$.content[0].ativo").value(true));
    }

    @Test
    void deveCriarFuncionario() throws Exception {
        FuncionarioResponseDTO funcionario = new FuncionarioResponseDTO(
                "func_1", "Ana", "ana@nhac.com", "11999999999", "Atendente", null, true, Instant.now());
        when(funcionarioService.criar(any(), eq(usuario))).thenReturn(funcionario);

        mockMvc.perform(post("/api/v1/lojista/funcionarios").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"nome":"Ana","email":"ana@nhac.com","telefone":"11999999999","senha":"Senha123","cargo":"Atendente"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("func_1"));
    }

    @Test
    void deveAtualizarFuncionario() throws Exception {
        when(funcionarioService.atualizar(eq("func_1"), any(), eq(usuario))).thenReturn(
                new FuncionarioResponseDTO("func_1", "Ana Atualizada", "ana@nhac.com", null,
                        "Gerente", null, true, null));

        mockMvc.perform(put("/api/v1/lojista/funcionarios/func_1").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"nome":"Ana Atualizada","telefone":"11999999999","cargo":"Gerente"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Atualizada"))
                .andExpect(jsonPath("$.cargo").value("Gerente"));
    }

    @Test
    void deveDesativarEReativarFuncionario() throws Exception {
        when(funcionarioService.reativar("func_1", usuario)).thenReturn(
                new FuncionarioResponseDTO("func_1", "Ana", "ana@nhac.com", null, "Atendente", null, true, null));

        mockMvc.perform(patch("/api/v1/lojista/funcionarios/func_1/ativar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/lojista/funcionarios/func_1").with(csrf()))
                .andExpect(status().isNoContent());
        verify(funcionarioService).desativar("func_1", usuario);
    }

    @Test
    void deveObterResumoDoPainel() throws Exception {
        PainelResumoDTO resumo = new PainelResumoDTO(true, new BigDecimal("100.00"), 2, 1, 3,
                List.of(new FaturamentoDiaDTO(LocalDate.of(2026, 9, 12), new BigDecimal("100.00"))),
                List.of(new PedidoResumoLojistaDTO("ped_1", "Cliente", 1, new BigDecimal("50.00"), null, null)));
        when(painelService.obterResumo(usuario)).thenReturn(resumo);

        mockMvc.perform(get("/api/v1/lojista/painel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lojaAberta").value(true))
                .andExpect(jsonPath("$.faturamentoHoje").value(100.00));
    }

    @Test
    void deveObterResumoFinanceiro() throws Exception {
        FinanceiroDTO financeiro = new FinanceiroDTO("SETE_DIAS",
                new ResumoFinanceiroDTO(new BigDecimal("100.00"), 2, new BigDecimal("50.00"), BigDecimal.ZERO),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(financeiroService.obterFinanceiro(usuario, PeriodoFinanceiro.TRINTA_DIAS)).thenReturn(financeiro);

        mockMvc.perform(get("/api/v1/lojista/financeiro").param("periodo", "TRINTA_DIAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("SETE_DIAS"))
                .andExpect(jsonPath("$.resumo.numeroPedidos").value(2));
    }

    @Test
    void deveListarConversasEMensagensEMarcarComoLida() throws Exception {
        ConversaResumoDTO conversa = new ConversaResumoDTO("conv_1", "cliente_1", "Cliente", ParticipanteTipo.CLIENTE, "Oi", Instant.now(), 1);
        MensagemDTO mensagem = new MensagemDTO("msg_1", "conv_1", RemetenteTipo.CLIENTE, "cliente_1", "Oi", Instant.now());
        when(chatService.listarConversasDaLoja(eq(usuario), any())).thenReturn(Page.empty());
        when(chatService.listarMensagens(eq("conv_1"), eq(usuario), any())).thenReturn(
                new PageImpl<>(List.of(mensagem), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/v1/lojista/conversas"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/lojista/conversas/conv_1/mensagens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("msg_1"));
        mockMvc.perform(patch("/api/v1/lojista/conversas/conv_1/lida").with(csrf()))
                .andExpect(status().isNoContent());
        verify(chatService).marcarComoLidaPelaLoja("conv_1", usuario);
    }

    @Test
    void deveAbrirConversaDoCliente() throws Exception {
        br.com.nhac.backend_nhac.domain.chat.Conversa conversa = new br.com.nhac.backend_nhac.domain.chat.Conversa();
        conversa.setId("conv_1");
        when(chatService.obterOuCriarConversa(eq("loja_1"), eq(usuario))).thenReturn(conversa);

        // O endpoint devolve JSON ({"id": "..."}), não o id cru: responder
        // text/plain com o id sem aspas quebrava o jsonDecode do app.
        mockMvc.perform(post("/api/v1/conversas/lojas/loja_1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv_1"));
    }
}
