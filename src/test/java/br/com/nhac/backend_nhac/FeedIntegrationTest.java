package br.com.nhac.backend_nhac;

import br.com.nhac.backend_nhac.domain.feed.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedIntegrationTest extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired FeedService feed;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private Usuario autor;
    private Usuario outro;

    @BeforeEach
    void criarUsuarios() {
        autor = usuarios.save(Usuario.builder().id("feed-autor").nome("Autor real")
                .telefone("11911111111").email("autor@feed.test").build());
        outro = usuarios.save(Usuario.builder().id("feed-outro").nome("Outro")
                .telefone("11922222222").email("outro@feed.test").build());
    }

    private FeedPostResponseDTO criar(String conteudo) {
        return feed.criar(autor, new FeedPostCreateDTO(conteudo, List.of(), List.of("#Nhac"), null, false, null));
    }

    @Test
    void curtidaComentarioIdempotenteEEstadoPorUsuario() throws Exception {
        String postId = criar("Post").id();
        String comentarioId = feed.comentar(postId, autor, new FeedComentarioCreateDTO("Comentário")).id();
        String path = "/api/v1/feed/posts/" + postId + "/comentarios/" + comentarioId + "/curtida";
        for (int i = 0; i < 2; i++) mockMvc.perform(put(path).with(user(outro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.curtidas").value(1))
                .andExpect(jsonPath("$.curtido").value(true));
        mockMvc.perform(get("/api/v1/feed/posts/" + postId + "/comentarios").with(user(autor)))
                .andExpect(jsonPath("$.content[0].curtidas").value(1))
                .andExpect(jsonPath("$.content[0].curtido").value(false))
                .andExpect(jsonPath("$.content[0].criadoEm").isNotEmpty());
        mockMvc.perform(get("/api/v1/feed/posts/" + postId + "/comentarios").with(user(outro)))
                .andExpect(jsonPath("$.content[0].curtido").value(true));
        for (int i = 0; i < 2; i++) mockMvc.perform(delete(path).with(user(outro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.curtidas").value(0))
                .andExpect(jsonPath("$.curtido").value(false));
        mockMvc.perform(put(path).with(user(outro))).andExpect(status().isOk());
        feed.remover(postId, autor);
        assertEquals(0, jdbc.queryForObject("select count(*) from tb_feed_comentario_curtidas", Integer.class));
    }

    @Test
    void respostaVinculadaValidaPublicacaoEIdempotencia() throws Exception {
        String postId = criar("Post").id();
        String pai = feed.comentar(postId, autor, new FeedComentarioCreateDTO("Pergunta")).id();
        String path = "/api/v1/feed/posts/" + postId + "/comentarios";
        String body = "{\"conteudo\":\"Resposta\",\"respostaAId\":\"" + pai + "\"}";
        for (int i = 0; i < 2; i++) mockMvc.perform(post(path).with(user(outro))
                .header("Idempotency-Key", "resposta-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.respostaAId").value(pai))
                .andExpect(jsonPath("$.respostaANome").value("Autor real"));
        assertEquals(2, feed.buscar(postId, autor).comentarios());
        String outroPost = criar("Outro post").id();
        mockMvc.perform(post("/api/v1/feed/posts/" + outroPost + "/comentarios").with(user(outro))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post(path).with(user(outro)).header("Idempotency-Key", "resposta-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Resposta\"}"))
                .andExpect(status().isConflict());
        feed.removerComentario(postId, pai, autor);
        mockMvc.perform(get(path).with(user(outro))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].respostaANome").value("Comentário excluído"));
        assertEquals(1, feed.buscar(postId, autor).comentarios());
    }

    @Test
    void compartilhamentoPublicoEscapaConteudoEDenunciaTemControleDeAcesso() throws Exception {
        String postId=criar("<script>alert(1)</script>").id();
        mockMvc.perform(get("/publicacao/"+postId)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
        mockMvc.perform(post("/api/v1/feed/posts/"+postId+"/denuncias").with(user(outro)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Spam\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ABERTA"));
        mockMvc.perform(get("/api/v1/feed/denuncias").with(user(outro))).andExpect(status().isForbidden());
        feed.remover(postId,autor);
        mockMvc.perform(get("/publicacao/"+postId)).andExpect(status().isNotFound());
    }
    @Test
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/feed/posts")).andExpect(status().isForbidden());
    }

    @Test
    void listaVaziaSemMock() throws Exception {
        mockMvc.perform(get("/api/v1/feed/posts").with(user(autor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void criaComAutorDaSessaoEPersisteContrato() throws Exception {
        mockMvc.perform(post("/api/v1/feed/posts").with(user(autor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"Meu pedido chegou!\",\"imagens\":[\"https://example.com/foto.jpg\"],\"hashTags\":[\"#Nhac\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nomeUsuario").value("Autor real"))
                .andExpect(jsonPath("$.usuarioId").value(autor.getId()))
                .andExpect(jsonPath("$.curtidas").value(0))
                .andExpect(jsonPath("$.isPatrocinado").value(false));
        mockMvc.perform(get("/api/v1/feed/posts").with(user(outro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].conteudo").value("Meu pedido chegou!"));
    }

    @Test
    void validaConteudoImagensCategoriaEPaginacao() throws Exception {
        for (String body : List.of("{\"conteudo\":\" \"}",
                "{\"conteudo\":\"Oi\",\"imagens\":[\"javascript:alert(1)\"]}",
                "{\"conteudo\":\"Oi\",\"hashTags\":[\"sem-hash\"]}")) {
            mockMvc.perform(post("/api/v1/feed/posts").with(user(autor))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/feed/posts").with(user(autor)).param("categoria", "inexistente"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/feed/posts").with(user(autor)).param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteNaoPublicaPromocao() throws Exception {
        mockMvc.perform(post("/api/v1/feed/posts").with(user(autor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"Promoção\",\"isPatrocinado\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void impedeEditarOuExcluirPostDeOutro() throws Exception {
        String id = criar("Original").id();
        mockMvc.perform(put("/api/v1/feed/posts/" + id).with(user(outro))
                .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Alterado\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/feed/posts/" + id).with(user(outro)))
                .andExpect(status().isForbidden());
        assertEquals("Original", feed.buscar(id, autor).conteudo());
    }

    @Test
    void curtidasESalvosSaoIdempotentesEPrivados() throws Exception {
        String id = criar("Post").id();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/v1/feed/posts/" + id + "/curtida").with(user(outro)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.curtidas").value(1))
                    .andExpect(jsonPath("$.curtido").value(true));
            mockMvc.perform(put("/api/v1/feed/posts/" + id + "/salvo").with(user(outro)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.salvos").value(1));
        }
        assertFalse(feed.buscar(id, autor).curtido());
        assertEquals(1, feed.listarSalvos(outro, 0, 20).getTotalElements());
        assertEquals(0, feed.listarSalvos(autor, 0, 20).getTotalElements());
        for (int i = 0; i < 2; i++) {
            feed.interagir(id, outro, FeedInteracao.Tipo.CURTIDA, false);
            feed.interagir(id, outro, FeedInteracao.Tipo.SALVO, false);
        }
        assertEquals(0, feed.buscar(id, outro).curtidas());
        assertEquals(0, feed.buscar(id, outro).salvos());
    }

    @Test
    void ordenaEmAltaEFiltraPromocoesSemInventarPosts() {
        String primeiro = criar("Primeiro").id();
        criar("Segundo");
        feed.interagir(primeiro, outro, FeedInteracao.Tipo.CURTIDA, true);
        assertEquals(primeiro, feed.listar(autor, "Em Alta", 0, 20).getContent().getFirst().id());
        assertEquals(0, feed.listar(autor, "Promoções", 0, 20).getTotalElements());
        assertEquals(2, feed.listar(autor, "Novidades", 0, 1).getTotalPages());
    }

    @Test
    void comentariosReaisTemPermissoesEContagens() throws Exception {
        String id = criar("Post").id();
        var comentario = feed.comentar(id, outro, new FeedComentarioCreateDTO("Gostei"));
        mockMvc.perform(get("/api/v1/feed/posts/" + id + "/comentarios").with(user(autor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].nomeUsuario").value("Outro"))
                .andExpect(jsonPath("$.content[0].isAuthor").value(false));
        assertEquals(1, feed.buscar(id, autor).comentarios());
        // Autor do post pode moderar comentários do próprio post.
        feed.removerComentario(id, comentario.id(), autor);
        assertEquals(0, feed.buscar(id, autor).comentarios());
    }

    @Test
    void exclusaoRemoveDependenciasEOutroPostNaoPodeApagarComentario() throws Exception {
        String id = criar("Post").id();
        String outroPost = criar("Outro post").id();
        var comentario = feed.comentar(id, outro, new FeedComentarioCreateDTO("Comentário"));
        mockMvc.perform(delete("/api/v1/feed/posts/" + outroPost + "/comentarios/" + comentario.id())
                .with(user(autor))).andExpect(status().isNotFound());
        feed.interagir(id, outro, FeedInteracao.Tipo.CURTIDA, true);
        feed.remover(id, autor);
        mockMvc.perform(get("/api/v1/feed/posts/" + id).with(user(autor))).andExpect(status().isNotFound());
        assertEquals(1, feed.listar(autor, "Novidades", 0, 20).getTotalElements());
    }

    @Test
    void adminPodeEditarEExcluirPostDeTerceiro() throws Exception {
        Usuario admin = usuarios.save(Usuario.builder().id("feed-admin").nome("Admin")
                .telefone("11933333333").email("admin@feed.test").papel(Papel.ADMIN).build());
        String id = criar("Original").id();
        mockMvc.perform(put("/api/v1/feed/posts/" + id).with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Moderado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conteudo").value("Moderado"))
                .andExpect(jsonPath("$.usuarioId").value(autor.getId()));
        mockMvc.perform(delete("/api/v1/feed/posts/" + id).with(user(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/feed/posts/" + id).with(user(autor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void autorInativoNaoApareceNasListagensNemNosDetalhes() throws Exception {
        String id = criar("Oculto").id();
        feed.interagir(id, outro, FeedInteracao.Tipo.SALVO, true);
        autor.setAtivo(false);
        usuarios.save(autor);
        for (String categoria : List.of("Destaques", "Novidades", "Em Alta", "Promoções"))
            assertEquals(0, feed.listar(outro, categoria, 0, 20).getTotalElements());
        assertEquals(0, feed.listarSalvos(outro, 0, 20).getTotalElements());
        mockMvc.perform(get("/api/v1/feed/posts/" + id).with(user(outro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizaColecoesPersistidasERemoveValoresAntigos() {
        var original = feed.criar(autor, new FeedPostCreateDTO("Original",
                List.of("https://example.com/antiga.jpg"), List.of("#Antiga"), null, false, null));
        feed.atualizar(original.id(), autor, new FeedPostCreateDTO("Novo",
                List.of("https://example.com/nova.jpg"), List.of("#Nova", "#Nova"), null, false, null));
        var atualizado = feed.buscar(original.id(), outro);
        assertEquals(List.of("https://example.com/nova.jpg"), atualizado.imagens());
        assertEquals(List.of("#Nova"), atualizado.hashTags());
        feed.atualizar(original.id(), autor, new FeedPostCreateDTO("Sem mídia", null, null, null, false, null));
        assertTrue(feed.buscar(original.id(), autor).imagens().isEmpty());
        assertTrue(feed.buscar(original.id(), autor).hashTags().isEmpty());
    }

    @Test
    void contagensDessintonizadasNaoFicamNegativas() {
        String id = criar("Contagens").id();
        feed.interagir(id, outro, FeedInteracao.Tipo.CURTIDA, true);
        feed.interagir(id, outro, FeedInteracao.Tipo.SALVO, true);
        var comentario = feed.comentar(id, outro, new FeedComentarioCreateDTO("Teste"));
        jdbc.update("update tb_feed_posts set curtidas = 0, salvos = 0, comentarios = 0 where id = ?", id);
        feed.interagir(id, outro, FeedInteracao.Tipo.CURTIDA, false);
        feed.interagir(id, outro, FeedInteracao.Tipo.SALVO, false);
        feed.removerComentario(id, comentario.id(), autor);
        var post = feed.buscar(id, outro);
        assertEquals(0, post.curtidas());
        assertEquals(0, post.salvos());
        assertEquals(0, post.comentarios());
    }

    @Test
    void curtidasConcorrentesDoMesmoUsuarioContamUmaVez() throws Exception {
        String id = criar("Concorrência").id();
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tarefas = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) tarefas.add(executor.submit(() -> {
                try { inicio.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                feed.interagir(id, outro, FeedInteracao.Tipo.CURTIDA, true);
            }));
            inicio.countDown();
            for (Future<?> tarefa : tarefas) tarefa.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, feed.buscar(id, autor).curtidas());
    }

    @Test
    void recentesEAutorConsultamTodasAsPaginasEPermissoes() throws Exception {
        String id=criar("Discussão").id();
        for (int i=0;i<23;i++) feed.comentar(id,outro,new FeedComentarioCreateDTO("Antigo "+i));
        var resposta=feed.comentar(id,autor,new FeedComentarioCreateDTO("Resposta do autor"));
        mockMvc.perform(get("/api/v1/feed/posts/"+id+"/comentarios").with(user(outro)).param("ordem","Recentes").param("size","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(resposta.id()))
            .andExpect(jsonPath("$.content[0].podeExcluir").value(false));
        mockMvc.perform(get("/api/v1/feed/posts/"+id+"/comentarios").with(user(autor)).param("autor","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(resposta.id()))
            .andExpect(jsonPath("$.content[0].podeExcluir").value(true));
        assertNotNull(feed.buscar(id,outro).topComment());
    }
    @Test
    void repetePostEComentarioSemDuplicarEMantemEscopoPorUsuario() throws Exception {
        String body="{\"conteudo\":\"Tentativa\"}";
        var primeira=mockMvc.perform(post("/api/v1/feed/posts").with(user(autor)).header("Idempotency-Key","post-1")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        String id=objectMapper.readTree(primeira.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/feed/posts").with(user(autor)).header("Idempotency-Key","post-1")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(post("/api/v1/feed/posts").with(user(autor)).header("Idempotency-Key","post-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Outro\"}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/feed/posts").with(user(outro)).header("Idempotency-Key","post-1")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        for (int i=0;i<2;i++) mockMvc.perform(post("/api/v1/feed/posts/"+id+"/comentarios").with(user(outro))
            .header("Idempotency-Key","comentario-1").contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Gostei\"}"))
            .andExpect(status().isCreated());
        assertEquals(1,feed.buscar(id,autor).comentarios());
        assertEquals(2,feed.listar(autor,"Novidades",0,20).getTotalElements());
    }
    @Test
    void destaquesOrdenamPorSalvosENovidadesPorData() {
        String primeiro=criar("Salvo").id();
        criar("Mais novo");
        feed.interagir(primeiro,outro,FeedInteracao.Tipo.SALVO,true);
        assertEquals(primeiro,feed.listar(autor,"Destaques",0,20).getContent().getFirst().id());
        assertNotEquals(primeiro,feed.listar(autor,"Novidades",0,20).getContent().getFirst().id());
        assertTrue(feed.buscar(primeiro,autor).podeEditar());
        assertFalse(feed.buscar(primeiro,outro).podeEditar());
    }
}
