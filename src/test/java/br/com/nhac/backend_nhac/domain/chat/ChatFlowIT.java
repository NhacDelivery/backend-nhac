package br.com.nhac.backend_nhac.domain.chat;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.entregador.StatusOperacional;
import br.com.nhac.backend_nhac.domain.entregador.TipoVeiculo;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;

/**
 * Cobre o fluxo REST do chat: abrir/obter conversa (idempotente), listar conversas da loja
 * com contador de não lidas, histórico de mensagens, marcar como lida, e isolamento entre
 * lojas diferentes. O ENVIO de mensagem em si é via WebSocket (fora do escopo de um teste
 * MockMvc simples) — aqui ele é exercitado chamando ChatService.enviarMensagem() diretamente
 * (mesmo código que o ChatWebSocketController chama), o que já cobre a lógica de negócio
 * (contador de não lidas, resolução de remetenteTipo, preview da última mensagem).
 */
public class ChatFlowIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LojaRepository lojaRepository;
    @Autowired
    private ConversaRepository conversaRepository;
    @Autowired
    private MensagemRepository mensagemRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private EntregadorRepository entregadorRepository;

    private Usuario donoA;
    private Usuario donoB;
    private Usuario cliente;
    private Loja lojaA;
    private Loja lojaB;
    private String tokenDonoA;
    private String tokenDonoB;
    private String tokenCliente;

    @BeforeEach
    void prepararDados() {
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        donoA = criarUsuario("dono.chat.a@teste.com", Papel.LOJISTA);
        donoB = criarUsuario("dono.chat.b@teste.com", Papel.LOJISTA);
        cliente = criarUsuario("cliente.chat@teste.com", Papel.CLIENTE);

        lojaA = criarLoja("loja-chat-a", donoA.getId());
        lojaB = criarLoja("loja-chat-b", donoB.getId());

        tokenDonoA = tokenService.gerarToken(donoA);
        tokenDonoB = tokenService.gerarToken(donoB);
        tokenCliente = tokenService.gerarToken(cliente);
    }

    private Usuario criarUsuario(String email, Papel papel) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome("Usuario " + email);
        usuario.setEmail(email);
        usuario.setSenha("senha123");
        usuario.setTelefone("11900000000");
        usuario.setPapel(papel);
        return usuarioRepository.save(usuario);
    }

    private Loja criarLoja(String id, String usuarioId) {
        Loja loja = new Loja();
        loja.setId(id);
        loja.setNome("Loja " + id);
        loja.setUsuarioId(usuarioId);
        loja.setAberto(true);
        DadosOperacionais dadosOp = new DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        return lojaRepository.save(loja);
    }

    /**
     * Abre/obtém a conversa e devolve o id lendo o JSON da resposta
     * ({"id":"conv_..."}). Não usar o corpo cru: ele é JSON, não o id solto.
     */
    private String abrirConversaComLoja(String lojaId) throws Exception {
        String corpo = mockMvc.perform(post("/api/v1/conversas/lojas/" + lojaId)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    @Test
    void reenvioComMesmoIdentificadorNaoDuplicaMensagem() throws Exception {
        String conversaId = abrirConversaComLoja(lojaA.getId());
        String id = UUID.randomUUID().toString();
        MensagemDTO primeira = chatService.enviarMensagem(conversaId, cliente, "Cheguei na loja", id);
        MensagemDTO repetida = chatService.enviarMensagem(conversaId, cliente, "Cheguei na loja", id);
        MensagemDTO segunda = chatService.enviarMensagem(conversaId, cliente, "Cheguei na loja", UUID.randomUUID().toString());

        assertEquals(primeira.id(), repetida.id());
        assertTrue(!primeira.id().equals(segunda.id()));
        assertEquals(2, mensagemRepository.countByConversaId(conversaId));
        mockMvc.perform(get("/api/v1/lojista/conversas").header("Authorization", "Bearer " + tokenDonoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].naoLidas").value(2));
    }

    @Test
    void fluxoCompletoDeConversa() throws Exception {
        // 1. Cliente abre uma conversa com a loja A
        String conversaId = abrirConversaComLoja(lojaA.getId());

        // 2. Chamar de novo é idempotente: devolve a mesma conversa
        String conversaIdRepetida = abrirConversaComLoja(lojaA.getId());
        assertEquals(conversaId, conversaIdRepetida);

        // 3. Cliente "envia" 2 mensagens (via ChatService, mesmo caminho usado pelo WebSocket)
        chatService.enviarMensagem(conversaId, cliente, "Oi, meu pedido já saiu?");
        chatService.enviarMensagem(conversaId, cliente, "Já faz 40 minutos");

        // 4. Dono A vê a conversa na listagem, com 2 não lidas e o nome do cliente
        mockMvc.perform(get("/api/v1/lojista/conversas").header("Authorization", "Bearer " + tokenDonoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].naoLidas").value(2))
                .andExpect(jsonPath("$.content[0].clienteNome").value(cliente.getNome()))
                .andExpect(jsonPath("$.content[0].ultimaMensagemPreview").value("Já faz 40 minutos"));

        // 5. Dono A vê o histórico de mensagens
        mockMvc.perform(get("/api/v1/lojista/conversas/" + conversaId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenDonoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // 6. Dono A responde (via ChatService) e o tipo é resolvido corretamente como LOJA
        MensagemDTO resposta = chatService.enviarMensagem(conversaId, donoA, "Já está a caminho!");
        assertEquals(RemetenteTipo.LOJA, resposta.remetenteTipo());

        // 7. Dono A marca a conversa como lida
        mockMvc.perform(patch("/api/v1/lojista/conversas/" + conversaId + "/lida")
                        .header("Authorization", "Bearer " + tokenDonoA))
                .andExpect(status().isNoContent());

        // 8. Não lidas volta a zero
        mockMvc.perform(get("/api/v1/lojista/conversas").header("Authorization", "Bearer " + tokenDonoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].naoLidas").value(0));

        // 9. Dono B (loja diferente) NÃO enxerga essa conversa: nem na lista, nem por ID direto
        mockMvc.perform(get("/api/v1/lojista/conversas").header("Authorization", "Bearer " + tokenDonoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/lojista/conversas/" + conversaId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenDonoB))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDeveDeixarUsuarioForaDaConversaEnviarMensagem() {
String conversaId = chatService.obterOuCriarConversa(lojaA.getId(), cliente).getId();

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                br.com.nhac.backend_nhac.exceptions.AcessoNegadoException.class,
                () -> chatService.enviarMensagem(conversaId, donoB, "Não sou dessa loja")
        ).getMessage().contains("Acesso negado"));
    }

    @Test
    void deveRecusarListarConversasSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/lojista/conversas"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }

    @Test
    void clienteListaConversaComLojaEEnviaPeloRest() throws Exception {
        String conversaId = abrirConversaComLoja(lojaA.getId());
        mockMvc.perform(post("/api/v1/conversas/" + conversaId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"Olá loja\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remetenteTipo").value("CLIENTE"));
        chatService.enviarMensagem(conversaId, donoA, "Olá cliente");
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("LOJA"))
                .andExpect(jsonPath("$.content[0].interlocutor.id").value(lojaA.getId()))
                .andExpect(jsonPath("$.content[0].naoLidas").value(1));
        mockMvc.perform(patch("/api/v1/conversas/" + conversaId + "/lida")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isNoContent());
        assertEquals(0, conversaRepository.findById(conversaId).orElseThrow().getNaoLidasCliente());
    }

    @Test
    void clienteComPerfilEntregadorAtivoDeveUsarOsDoisPapeis() throws Exception {
        Entregador perfil = Entregador.builder()
                .id("ent-chat")
                .usuario(cliente)
                .cnh("CNH-CHAT")
                .placaVeiculo("ABC1D23")
                .tipoVeiculo(TipoVeiculo.MOTO)
                .statusOperacional(StatusOperacional.ONLINE)
                .ativo(true)
                .build();
        entregadorRepository.saveAndFlush(perfil);

        // O mesmo token/usuário continua CLIENTE e consegue abrir o chat normal.
        mockMvc.perform(post("/api/v1/conversas/lojas/" + lojaA.getId())
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());

        // AutoridadesFactory adiciona ROLE_ENTREGADOR a partir do vínculo ativo,
        // e ChatService valida o perfil real em vez de Usuario.papel.
        mockMvc.perform(post("/api/v1/entregador/conversas/lojas/" + lojaA.getId())
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(get("/api/v1/entregador/conversas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].interlocutor.id").value(lojaA.getId()));
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));

        assertEquals(Papel.CLIENTE,
                usuarioRepository.findById(cliente.getId()).orElseThrow().getPapel());
    }

}
