package br.com.nhac.backend_nhac.domain.lojista;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissoesCargoLojistaIT extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired LojaRepository lojas;
    @Autowired ProdutoRepository produtos;
    @Autowired TokenService tokens;
    Loja loja;
    Produto produto;
    Usuario dono;

    @BeforeEach void preparar() {
        dono = usuario(Papel.LOJISTA, null, null);
        loja = new Loja();
        loja.setId(UUID.randomUUID().toString());
        loja.setNome("Loja cargos");
        loja.setUsuarioId(dono.getId());
        loja.setAberto(true);
        loja.setDadosOperacionais(new DadosOperacionais());
        loja.setEndereco(new EnderecoLoja("Rua", "10", "Cidade", "SP", "01001-000", "Bairro", null));
        loja.setHorariosFuncionamento(new HorariosFuncionamento());
        loja.setFormasPagamento(new FormasPagamento());
        lojas.saveAndFlush(loja);
        produto = new Produto();
        produto.setId(UUID.randomUUID().toString());
        produto.setNome("Produto cargos");
        produto.setPreco(BigDecimal.TEN);
        produto.setCategoriaMenu("Lanches");
        produto.setLoja(loja);
        produto.setEstoque(10);
        produtos.saveAndFlush(produto);
    }

    private Usuario usuario(Papel papel, String cargo, String lojaId) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString()); u.setNome("Usuário cargos");
        u.setEmail(u.getId() + "@teste.com"); u.setSenha("senha-hash-fixture");
        u.setPapel(papel); u.setCargo(cargo); u.setLojaVinculadaId(lojaId); u.setAtivo(true);
        return usuarios.saveAndFlush(u);
    }
    private void leitura(String path, String token, int statusEsperado) throws Exception {
        mockMvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(statusEsperado));
    }
    private void estoque(String token, int valor, int statusEsperado) throws Exception {
        mockMvc.perform(patch("/api/v1/produtos/" + produto.getId() + "/estoque")
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"estoque\":" + valor + "}")).andExpect(status().is(statusEsperado));
    }
    @Test void atendenteNaoAcessaCatalogoPainelFinanceiroOuEquipeMesmoPorHttp() throws Exception {
        String token = tokens.gerarToken(usuario(Papel.FUNCIONARIO, "Atendente", loja.getId()));
        for (String path : new String[]{"/lojista/produtos", "/lojista/produtos/" + produto.getId(),
                "/lojista/painel", "/lojista/financeiro", "/lojista/funcionarios"}) leitura(path, token, 403);
        mockMvc.perform(post("/api/v1/produtos").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(payload(null))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/produtos/" + produto.getId()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(payload(true))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/produtos/" + produto.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/produtos/" + produto.getId() + "/ativar").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        estoque(token, 999, 403);
        mockMvc.perform(patch("/api/v1/lojas/" + loja.getId() + "/abertura").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"isAberto\":false}")).andExpect(status().isForbidden());
        assertEquals(1, produtos.count());
        assertEquals(10, produtos.findById(produto.getId()).orElseThrow().getEstoque());
        assertTrue(lojas.findById(loja.getId()).orElseThrow().isAberto());
        leitura("/lojista/pedidos", token, 200);
        leitura("/lojista/conversas", token, 200);
    }
    @Test void gerenteGerenciaCatalogoMasNaoFinanceiroERebaixamentoValeComMesmoToken() throws Exception {
        Usuario gerente = usuario(Papel.FUNCIONARIO, " Gerente ", loja.getId());
        String token = tokens.gerarToken(gerente);
        leitura("/lojista/painel", token, 200); leitura("/lojista/produtos", token, 200);
        estoque(token, 25, 200); leitura("/lojista/financeiro", token, 403);
        gerente.setCargo("Atendente"); usuarios.saveAndFlush(gerente);
        estoque(token, 99, 403);
        assertEquals(25, produtos.findById(produto.getId()).orElseThrow().getEstoque());
    }
    @Test void administradorFuncionarioAcessaFinanceiroMasNaoEquipeOuConfiguracaoDaLoja() throws Exception {
        String token = tokens.gerarToken(usuario(Papel.FUNCIONARIO, "Administrador", loja.getId()));
        leitura("/lojista/financeiro", token, 200); leitura("/lojista/funcionarios", token, 403);
        mockMvc.perform(patch("/api/v1/lojas/" + loja.getId() + "/localizacao").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"latitude\":-23.5,\"longitude\":-46.6}"))
                .andExpect(status().isForbidden());
    }
    @Test void produtoInativoNasceInativoEContinuaEditavelNoPainel() throws Exception {
        String token = tokens.gerarToken(dono);
        String body = mockMvc.perform(post("/api/v1/produtos").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(payload(false)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();
        assertFalse(produtos.findById(id).orElseThrow().isAtivo());
        mockMvc.perform(get("/api/v1/produtos/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/lojista/produtos/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(patch("/api/v1/produtos/" + id + "/ativar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/produtos/" + id)).andExpect(status().isOk());
    }
    @Test void criacaoAntigaSemDisponibilidadeMantemProdutoAtivo() throws Exception {
        String body = mockMvc.perform(post("/api/v1/produtos").header("Authorization", "Bearer " + tokens.gerarToken(dono))
                .contentType("application/json").content(payload(null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertTrue(produtos.findById(objectMapper.readTree(body).get("id").asText()).orElseThrow().isAtivo());
    }
    @Test void cargoAusenteDesconhecidoOuVinculoInexistenteNaoLiberaCatalogo() throws Exception {
        for (String cargo : new String[]{null, "Cargo desconhecido"}) {
            String token = tokens.gerarToken(usuario(Papel.FUNCIONARIO, cargo, loja.getId()));
            leitura("/lojista/produtos", token, 403);
            leitura("/lojista/financeiro", token, 403);
            leitura("/lojista/pedidos", token, 200);
        }
        String token = tokens.gerarToken(usuario(Papel.FUNCIONARIO, "Gerente", "outra-loja"));
        estoque(token, 99, 403);
        assertEquals(10, produtos.findById(produto.getId()).orElseThrow().getEstoque());
    }

    private String payload(Boolean ativo) {
        return "{\"nome\":\"Produto novo\",\"preco\":12,\"categoriaMenu\":\"Lanches\",\"estoque\":5"
                + (ativo == null ? "" : ",\"isAtivo\":" + ativo) + "}";
    }
}
