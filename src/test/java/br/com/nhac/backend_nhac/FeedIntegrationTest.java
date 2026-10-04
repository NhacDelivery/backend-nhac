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
}
