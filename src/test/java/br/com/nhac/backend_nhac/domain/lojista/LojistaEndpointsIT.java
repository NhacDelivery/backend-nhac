package br.com.nhac.backend_nhac.domain.lojista;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;

class LojistaEndpointsIT extends AbstractIntegrationTest {

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @BeforeEach
    void limparDados() {
        pedidoRepository.deleteAll();
        produtoRepository.deleteAll();
        lojaRepository.deleteAll();
    }

    @Test
    void deveRecusarListagemDeProdutosSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/lojista/produtos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void deveRecusarListagemDePedidosSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/lojista/pedidos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void deveIsolarDadosEntreLojistasDiferentes() throws Exception {
        // Criar lojista A
        String emailA = "lojista.a@nhac.com.br";
        String senhaA = "senhaForte123";
        String provaA = criarCodigoVerificadoPara(emailA);
        RegistroRequestDTO registroA = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Lojista A",
                emailA,
                "11988887771",
                senhaA, provaA
        );
        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroA)))
                .andExpect(status().isCreated());
        String tokenA = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(emailA, senhaA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Criar lojista B
        String emailB = "lojista.b@nhac.com.br";
        String senhaB = "senhaForte123";
        String provaB = criarCodigoVerificadoPara(emailB);
        RegistroRequestDTO registroB = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Lojista B",
                emailB,
                "11988887772",
                senhaB, provaB
        );
        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroB)))
                .andExpect(status().isCreated());
        String tokenB = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(emailB, senhaB))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Criar loja para A
        String jsonLojaA = jsonCriacaoLoja("loja-a");
        String lojaIdA = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLojaA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Re-autenticar lojista A para obter token com papel LOJISTA
        tokenA = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(emailA, senhaA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Criar loja para B
        String jsonLojaB = jsonCriacaoLoja("loja-b");
        String lojaIdB = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLojaB))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Re-autenticar lojista B para obter token com papel LOJISTA
        tokenB = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(emailB, senhaB))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Lojista A cadastra produto na sua loja
        String jsonProdutoA = """
                {
                  "nome": "Produto da Loja A",
                  "descricao": "Descrição do produto A",
                  "preco": 25.00,
                  "imagemUrl": "https://example.com/prod-a.jpg",
                  "categoriaMenu": "Lanches"
                }
                """;
        mockMvc.perform(post("/api/v1/produtos?lojaId=" + lojaIdA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonProdutoA))
                .andExpect(status().isCreated());

        // Lojista B cadastra produto na sua loja
        String jsonProdutoB = """
                {
                  "nome": "Produto da Loja B",
                  "descricao": "Descrição do produto B",
                  "preco": 30.00,
                  "imagemUrl": "https://example.com/prod-b.jpg",
                  "categoriaMenu": "Lanches"
                }
                """;
        mockMvc.perform(post("/api/v1/produtos?lojaId=" + lojaIdB)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonProdutoB))
                .andExpect(status().isCreated());

        // Verificar que lojista A vê apenas seu produto
        String produtosAJson = mockMvc.perform(get("/api/v1/lojista/produtos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var produtosA = objectMapper.readTree(produtosAJson).get("content");
        assertEquals(1, produtosA.size(), "Lojista A deve ver apenas 1 produto");
        assertEquals("Produto da Loja A", produtosA.get(0).get("nome").asText());

        // Verificar que lojista B vê apenas seu produto
        String produtosBJson = mockMvc.perform(get("/api/v1/lojista/produtos")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var produtosB = objectMapper.readTree(produtosBJson).get("content");
        assertEquals(1, produtosB.size(), "Lojista B deve ver apenas 1 produto");
        assertEquals("Produto da Loja B", produtosB.get(0).get("nome").asText());

        // Verificar que lojista B não vê pedidos (nenhum pedido criado ainda)
        mockMvc.perform(get("/api/v1/lojista/pedidos")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private String jsonCriacaoLoja(String sufixo) {
        return """
                {
                  "nome": "Mercado %s",
                  "descricao": "Orgânicos",
                  "categoria": "Restaurantes",
                  "imagemUrl": "https://example.com/banner-%s.jpg",
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
                """.formatted(sufixo, sufixo);
    }
}
