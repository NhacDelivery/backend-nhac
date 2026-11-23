package br.com.nhac.backend_nhac.domain.cupom;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CupomFlowIT extends AbstractIntegrationTest {
    @Autowired br.com.nhac.backend_nhac.domain.loja.LojaRepository lojas;
    @Autowired CupomService service;
    @Autowired CupomRepository cupons;
    @Autowired UsuarioRepository usuarios;
    @Autowired TokenService tokens;
    String token;

    @BeforeEach
    void criarUsuario() {
        Usuario usuario = new Usuario();
        usuario.setId("cliente-cupom");
        usuario.setNome("Cliente");
        usuario.setEmail("cliente-cupom@exemplo.com");
        usuario.setTelefone("11999999999");
        usuarios.save(usuario);
        token = tokens.gerarToken(usuario);
    }

    @Test
    void lojistaPublicaPromocaoParaSeuCheckoutEPodeDesativar() throws Exception {
        Usuario dono = new Usuario();
        dono.setId("dono");
        dono.setNome("Lojista");
        dono.setEmail("dono@exemplo.com");
        dono.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);
        usuarios.save(dono);
        var loja = new br.com.nhac.backend_nhac.domain.loja.Loja();
        loja.setId("loja");
        loja.setNome("Loja");
        loja.setUsuarioId(dono.getId());
        lojas.save(loja);
        String cliente = "Bearer " + token;
        String lojista = "Bearer " + tokens.gerarToken(dono);
        String recurso = "/api/v1/lojista/cupons";
        var hoje = java.time.LocalDate.now();
        String dados = """
                {"codigo":"LOJA10","tipo":"PERCENTUAL","valor":10,"minimo":20,
                 "inicio":"%s","fim":"%s","limite":1,"ativo":true}
                """.formatted(hoje, hoje.plusDays(1));
        mockMvc.perform(post(recurso).header("Authorization", cliente)
                .contentType(MediaType.APPLICATION_JSON).content(dados)).andExpect(status().isForbidden());
        var resposta = mockMvc.perform(post(recurso).header("Authorization", lojista)
                .contentType(MediaType.APPLICATION_JSON).content(dados)).andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(resposta.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/cupons").param("lojaId", "loja").header("Authorization", cliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        String validar = """
                {"cupomId":"%s","lojaId":"loja","subtotal":60}
                """.formatted(id);
        mockMvc.perform(post("/api/v1/cupons/validar").header("Authorization", cliente)
                .contentType(MediaType.APPLICATION_JSON).content(validar))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descontoAplicado").value(6));
        mockMvc.perform(patch(recurso + "/" + id).header("Authorization", lojista)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/v1/cupons").param("lojaId", "loja").header("Authorization", cliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void resgataListaEValidaSemConsumir() throws Exception {
        var result = mockMvc.perform(post("/api/v1/cupons/boas-vindas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.desconto").value(5)).andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/cupons").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(post("/api/v1/cupons/validar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cupomId\":\"" + id + "\",\"subtotal\":30}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descontoAplicado").value(5));
        assertEquals(0, cupons.findById(id).orElseThrow().getUsosAtuais());
        mockMvc.perform(post("/api/v1/cupons/validar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cupomId\":\"" + id + "\",\"subtotal\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cuponsExigemAutenticacao() throws Exception {
        mockMvc.perform(post("/api/v1/cupons/boas-vindas")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/cupons")).andExpect(status().is4xxClientError());
    }

    @Test
    void doisResgatesConcorrentesGeramUmCupomEUmUnicoUso() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<String> resgatar = () -> { inicio.await(); return service.ganharBoasVindas("cliente-cupom").id(); };
            Future<String> primeiro = executor.submit(resgatar);
            Future<String> segundo = executor.submit(resgatar);
            inicio.countDown();
            String id = primeiro.get(15, TimeUnit.SECONDS);
            assertEquals(id, segundo.get(15, TimeUnit.SECONDS));
            assertEquals(1, cupons.count());
            var consumirInicio = new CountDownLatch(1);
            Callable<Boolean> consumir = () -> {
                consumirInicio.await();
                try { service.consumir("cliente-cupom", id, new BigDecimal("30")); return true; }
                catch (br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException e) { return false; }
            };
            Future<Boolean> uso1 = executor.submit(consumir);
            Future<Boolean> uso2 = executor.submit(consumir);
            consumirInicio.countDown();
            assertNotEquals(uso1.get(15, TimeUnit.SECONDS), uso2.get(15, TimeUnit.SECONDS));
            assertEquals(1, cupons.findById(id).orElseThrow().getUsosAtuais());
        }
    }
}
