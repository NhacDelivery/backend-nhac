package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o fluxo ponta a ponta de funcionários: cadastro pelo dono, login próprio do
 * funcionário, permissão para mexer em produtos/pedidos da loja (valida o fix do
 * @PreAuthorize que faltava FUNCIONARIO em ProdutoController/PedidoController),
 * restrição de que só o dono gerencia a equipe, e o efeito de desativar um funcionário
 * (login bloqueado + token antigo também para de funcionar, valida o fix do SecurityFilter).
 */
public class FuncionarioFlowIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TokenService tokenService;

    private Usuario dono;
    private Loja loja;
    private String tokenDono;

    @BeforeEach
    void prepararDados() {
        produtoRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = new Usuario();
        dono.setId(UUID.randomUUID().toString());
        dono.setNome("Dono da Loja");
        dono.setEmail("dono.funcionarios@teste.com");
        dono.setSenha("senha123");
        dono.setTelefone("11999990000");
        dono.setPapel(Papel.LOJISTA);
        usuarioRepository.save(dono);

        loja = new Loja();
        loja.setId("loja-func-teste");
        loja.setNome("Loja Teste Funcionarios");
        loja.setUsuarioId(dono.getId());
        loja.setAberto(true);
        DadosOperacionais dadosOp = new DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        lojaRepository.save(loja);

        tokenDono = tokenService.gerarToken(dono);
    }

    @Test
    void fluxoCompletoDeFuncionario() throws Exception {
        // 1. Dono cadastra um funcionário
        String jsonCriacao = """
                {"nome":"Ana Atendente","email":"ana.funcionaria@teste.com","telefone":"11988887777","senha":"senhaForte123","cargo":"Atendente"}
                """;

        String respostaCriacao = mockMvc.perform(post("/api/v1/lojista/funcionarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cargo").value("Atendente"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();
        String funcionarioId = objectMapper.readTree(respostaCriacao).get("id").asText();

        // 2. Dono vê o funcionário na listagem
        mockMvc.perform(get("/api/v1/lojista/funcionarios").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nomeCompleto").value("Ana Atendente"));

        // 3. Funcionário loga com a própria senha e recebe papel FUNCIONARIO
        String loginJson = objectMapper.writeValueAsString(new LoginRequestDTO("ana.funcionaria@teste.com", "senhaForte123"));
        String loginResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("FUNCIONARIO"))
                .andReturn().getResponse().getContentAsString();
        String tokenFuncionario = objectMapper.readTree(loginResp).get("token").asText();

        // 4. Atendente vê pedidos, mas catálogo exige Gerente ou Administrador
        mockMvc.perform(get("/api/v1/lojista/produtos").header("Authorization", "Bearer " + tokenFuncionario))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/lojista/pedidos").header("Authorization", "Bearer " + tokenFuncionario))
                .andExpect(status().isOk());

        // 5. O dono promove para Gerente; o mesmo token recebe a permissão atualizada.
        String jsonProduto = """
                {"nome":"Coxinha","descricao":"Salgado frito","preco":8.50,"imagemUrl":"https://example.com/coxinha.jpg","categoriaMenu":"Salgados"}
                """;
        mockMvc.perform(post("/api/v1/produtos").header("Authorization", "Bearer " + tokenFuncionario)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonProduto))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/lojista/funcionarios/" + funcionarioId)
                        .header("Authorization", "Bearer " + tokenDono).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana Gerente\",\"cargo\":\"Gerente\"}"))
                .andExpect(status().isOk());
        String produtoResp = mockMvc.perform(post("/api/v1/produtos")
                        .header("Authorization", "Bearer " + tokenFuncionario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonProduto))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String produtoId = objectMapper.readTree(produtoResp).get("id").asText();

        // 6. Funcionário consegue desativar o produto que acabou de criar
        mockMvc.perform(delete("/api/v1/produtos/" + produtoId).header("Authorization", "Bearer " + tokenFuncionario))
                .andExpect(status().isNoContent());

        // 7. Funcionário NÃO pode cadastrar outro funcionário (só o dono gerencia a equipe)
        String jsonOutroFuncionario = """
                {"nome":"Outro Func","email":"outro.func@teste.com","telefone":"11977776666","senha":"outraSenha123","cargo":"Atendente"}
                """;
        mockMvc.perform(post("/api/v1/lojista/funcionarios")
                        .header("Authorization", "Bearer " + tokenFuncionario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonOutroFuncionario))
                .andExpect(status().isForbidden());

        // 8. Dono desativa a funcionária
        mockMvc.perform(delete("/api/v1/lojista/funcionarios/" + funcionarioId)
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isNoContent());

        // 9. Funcionária desativada não consegue mais logar (novo login deve ser recusado)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));

        // 10. O token antigo dela (emitido antes da desativação) também para de funcionar
        // imediatamente, sem precisar esperar expirar (valida o fix do SecurityFilter)
        mockMvc.perform(get("/api/v1/lojista/produtos").header("Authorization", "Bearer " + tokenFuncionario))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }

    @Test
    void naoDeveDeixarUmClienteComumGerenciarFuncionariosDeOutraLoja() throws Exception {
        Usuario cliente = new Usuario();
        cliente.setId(UUID.randomUUID().toString());
        cliente.setNome("Cliente Comum");
        cliente.setEmail("cliente.comum@teste.com");
        cliente.setSenha("senha123");
        cliente.setTelefone("11999991111");
        cliente.setPapel(Papel.CLIENTE);
        usuarioRepository.save(cliente);
        String tokenCliente = tokenService.gerarToken(cliente);

        mockMvc.perform(get("/api/v1/lojista/funcionarios").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
    }

    @Test
    void naoDeveCadastrarFuncionarioComEmailJaExistente() throws Exception {
        String jsonCriacao = """
                {"nome":"Duplicado","email":"%s","telefone":"11955554444","senha":"senhaForte123","cargo":"Atendente"}
                """.formatted(dono.getEmail());

        mockMvc.perform(post("/api/v1/lojista/funcionarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacao))
                .andExpect(status().isBadRequest());
    }
}
