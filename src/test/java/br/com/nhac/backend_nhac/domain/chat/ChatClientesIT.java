package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatClientesIT extends AbstractIntegrationTest {
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ConversaRepository conversas;
    @Autowired private MensagemRepository mensagens;
    @Autowired private ChatService service;
    @Autowired private TokenService tokens;
    private Usuario a, b, terceiro, lojista, admin;

    @BeforeEach
    void dados() {
        a = criar("a", Papel.CLIENTE);
        b = criar("b", Papel.CLIENTE);
        terceiro = criar("terceiro", Papel.CLIENTE);
        lojista = criar("lojista", Papel.LOJISTA);
        admin = criar("admin", Papel.ADMIN);
    }

    private Usuario criar(String id, Papel papel) {
        Usuario u = Usuario.builder().id(id).nome("Nome " + id).email(id + "@teste.com")
                .telefone("11900000000").senha("senha").papel(papel).build();
        return usuarios.saveAndFlush(u);
    }

    private String token(Usuario u) { return "Bearer " + tokens.gerarToken(u); }

    private String abrir(Usuario remetente, Usuario destino) throws Exception {
        String body = mockMvc.perform(post("/api/v1/conversas/clientes/" + destino.getId())
                        .header("Authorization", token(remetente)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void enviar(String id, Usuario u, String texto, String messageId) throws Exception {
        mockMvc.perform(post("/api/v1/conversas/" + id + "/mensagens")
                        .header("Authorization", token(u)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "conteudo", texto, "clientMessageId", messageId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remetenteUsuarioId").value(u.getId()))
                .andExpect(jsonPath("$.remetenteTipo").value("CLIENTE"));
    }

    private void verificarLista(Usuario u, int naoLidas, String interlocutor) throws Exception {
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", token(u)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.content[0].interlocutor.id").value(interlocutor))
                .andExpect(jsonPath("$.content[0].interlocutor.nome").value("Nome " + interlocutor))
                .andExpect(jsonPath("$.content[0].interlocutor.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].naoLidas").value(naoLidas));
    }

    @Test
    void fluxoBidirecionalComReenvioELeituraIndependente() throws Exception {
        String id = abrir(a, b);
        assertEquals(id, abrir(b, a));
        assertEquals(id, abrir(a, b));
        assertEquals(1, conversas.count());
        String mensagemId = UUID.randomUUID().toString();
        enviar(id, a, "Oi!", mensagemId);
        enviar(id, a, "Oi!", mensagemId);
        enviar(id, b, "Olá!", UUID.randomUUID().toString());
        assertEquals(2, mensagens.countByConversaId(id));
        verificarLista(a, 1, b.getId());
        verificarLista(b, 1, a.getId());
        for (Usuario u : java.util.List.of(a, b)) {
            mockMvc.perform(get("/api/v1/conversas/" + id + "/mensagens")
                            .param("size", "1").header("Authorization", token(u)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
        mockMvc.perform(patch("/api/v1/conversas/" + id + "/lida").header("Authorization", token(b)))
                .andExpect(status().isNoContent());
        verificarLista(a, 1, b.getId());
        verificarLista(b, 0, a.getId());
        mockMvc.perform(patch("/api/v1/conversas/" + id + "/lida").header("Authorization", token(a)))
                .andExpect(status().isNoContent());
        verificarLista(a, 0, b.getId());
    }

    @Test
    void terceirosELojistasEAdministradoresNaoAcessamChatPrivado() throws Exception {
        String id = abrir(a, b);
        assertTrue(service.podeAcessarConversa(id, a));
        assertTrue(service.podeAcessarConversa(id, b));
        for (Usuario u : java.util.List.of(terceiro, lojista, admin)) {
            assertFalse(service.podeAcessarConversa(id, u));
            mockMvc.perform(get("/api/v1/conversas/" + id + "/mensagens").header("Authorization", token(u)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/conversas/" + id + "/lida").header("Authorization", token(u)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/conversas/" + id + "/mensagens")
                            .header("Authorization", token(u)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"conteudo\":\"Intrusão\"}"))
                    .andExpect(status().isForbidden());
            assertThrows(br.com.nhac.backend_nhac.exceptions.AcessoNegadoException.class,
                    () -> service.enviarMensagem(id, u, "Intrusão WS"));
        }
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", token(terceiro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(0, mensagens.count());
    }

    @Test
    void rejeitaDestinatariosInvalidosEAutorSemPapelCliente() throws Exception {
        mockMvc.perform(post("/api/v1/conversas/clientes/a").header("Authorization", token(a)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/conversas/clientes/inexistente").header("Authorization", token(a)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversas/clientes/lojista").header("Authorization", token(a)))
                .andExpect(status().isBadRequest());
        b.setAtivo(false); usuarios.saveAndFlush(b);
        mockMvc.perform(post("/api/v1/conversas/clientes/b").header("Authorization", token(a)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/conversas/clientes/a").header("Authorization", token(lojista)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", token(lojista)))
                .andExpect(status().isForbidden());
        assertEquals(0, conversas.count());
    }

    @Test
    void validaJsonETamanhoDeMensagemEConflitoDeIdempotencia() throws Exception {
        String id = abrir(a, b);
        for (String body : java.util.List.of("{}", "{\"conteudo\":\"   \"}",
                "{\"conteudo\":\"Oi\",\"clientMessageId\":\"invalido\"}",
                objectMapper.writeValueAsString(java.util.Map.of("conteudo", "x".repeat(4001))))) {
            mockMvc.perform(post("/api/v1/conversas/" + id + "/mensagens")
                            .header("Authorization", token(a)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        String messageId = UUID.randomUUID().toString();
        enviar(id, a, "x".repeat(4000), messageId);
        mockMvc.perform(post("/api/v1/conversas/" + id + "/mensagens")
                        .header("Authorization", token(b)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("conteudo", "Outro", "clientMessageId", messageId))))
                .andExpect(status().isForbidden());
        assertEquals(1, mensagens.count());
        assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,
                () -> service.enviarMensagem(id, a, " "));
        assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,
                () -> service.enviarMensagem(id, a, "Oi", "invalido"));
    }

    @Test
    void listagemPaginadaMaisRecentePrimeiroELimitada() throws Exception {
        String ab = abrir(a, b);
        String ac = abrir(a, terceiro);
        // Datas distintas e explícitas evitam depender da precisão do relógio do banco.
        Conversa antiga = conversas.findById(ab).orElseThrow();
        antiga.setUltimaMensagemEm(java.time.Instant.parse("2026-01-01T00:00:00Z")); conversas.saveAndFlush(antiga);
        Conversa recente = conversas.findById(ac).orElseThrow();
        recente.setUltimaMensagemEm(java.time.Instant.parse("2026-01-02T00:00:00Z")); conversas.saveAndFlush(recente);
        mockMvc.perform(get("/api/v1/conversas").param("size", "1").header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(ac))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/conversas").param("size", "1").param("page", "1").header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(ab));
        mockMvc.perform(get("/api/v1/conversas").param("size", "999").header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void exigeTokenETraz404ParaConversaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/conversas"))
                .andExpect(r -> assertTrue(java.util.Set.of(401, 403).contains(r.getResponse().getStatus())));
        mockMvc.perform(post("/api/v1/conversas/clientes/b"))
                .andExpect(r -> assertTrue(java.util.Set.of(401, 403).contains(r.getResponse().getStatus())));
        mockMvc.perform(get("/api/v1/conversas/inexistente/mensagens").header("Authorization", token(a)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/conversas/inexistente/lida").header("Authorization", token(a)))
                .andExpect(status().isNotFound());
    }

    @Test
    void misturaChatsDeLojaEDiretoSemVazarDadosPessoais() throws Exception {
        var lojaRepository = webApplicationContext.getBean(br.com.nhac.backend_nhac.domain.loja.LojaRepository.class);
        var loja = new br.com.nhac.backend_nhac.domain.loja.Loja();
        loja.setId("loja-mista"); loja.setNome("Loja Mista"); loja.setUsuarioId(lojista.getId());
        loja.setEndereco(new br.com.nhac.backend_nhac.domain.loja.EnderecoLoja("Rua", "1", "Cidade", "SP", "00000-000", "Bairro", null));
        loja.setDadosOperacionais(new br.com.nhac.backend_nhac.domain.loja.DadosOperacionais());
        lojaRepository.saveAndFlush(loja);
        service.obterOuCriarConversa(loja.getId(), a);
        abrir(a, b);
        var pagina = conversas.listarDoParticipante(a.getId(), ParticipanteTipo.CLIENTE,
                org.springframework.data.domain.PageRequest.of(0, 20));
        pagina.stream().filter(c -> !c.isEntreClientes()).forEach(c ->
                assertTrue(org.hibernate.Hibernate.isInitialized(c.getLoja()),
                        "EntityGraph deve carregar a loja na consulta da página"));
        mockMvc.perform(get("/api/v1/conversas").header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/lojista/conversas").header("Authorization", token(lojista)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].clienteId").value(a.getId()));
    }

    @Test
    void aberturaSimultaneaNosDoisSentidosCriaUmaConversa() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primeira = pool.submit(() -> { largada.await(); return service.obterOuCriarConversaEntreClientes(b.getId(), a).getId(); });
            var segunda = pool.submit(() -> { largada.await(); return service.obterOuCriarConversaEntreClientes(a.getId(), b).getId(); });
            largada.countDown();
            assertEquals(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, conversas.count());
    }

    @Test
    void reenvioConcorrenteNaoDuplicaMensagemNemContador() throws Exception {
        String id = abrir(a, b);
        String messageId = UUID.randomUUID().toString();
        CountDownLatch largada = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primeira = pool.submit(() -> { largada.await(); return service.enviarMensagem(id, a, "Oi", messageId).id(); });
            var segunda = pool.submit(() -> { largada.await(); return service.enviarMensagem(id, a, "Oi", messageId).id(); });
            largada.countDown();
            assertEquals(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, mensagens.count());
        verificarLista(b, 1, a.getId());
    }
}
