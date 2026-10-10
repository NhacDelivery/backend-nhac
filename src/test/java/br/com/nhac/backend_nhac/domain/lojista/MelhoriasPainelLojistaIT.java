package br.com.nhac.backend_nhac.domain.lojista;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.FormasPagamento;
import br.com.nhac.backend_nhac.domain.loja.HorariosFuncionamento;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;
/**
 * Cobre as peças que faltavam no commit mais recente e foram adicionadas nesta rodada:
 * abrir/fechar loja (PATCH .../abertura), reposição de estoque, edição de produto
 * desativado, upload de imagem (em modo mock) e preferências de notificação.
 */
public class MelhoriasPainelLojistaIT extends AbstractIntegrationTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LojaRepository lojaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private TokenService tokenService;

    private Usuario dono;
    private Usuario funcionario;
    private Usuario outroUsuario;
    private Loja loja;
    private Produto produtoInativo;
    
    // Declarando os tokens novamente
    private String tokenDono;
    private String tokenFuncionario;
    private String tokenOutroUsuario;

    @BeforeEach
    void prepararDados() {
        produtoRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = criarUsuario("dono.melhorias@teste.com", Papel.LOJISTA, null);
        outroUsuario = criarUsuario("outro.melhorias@teste.com", Papel.CLIENTE, null);

        loja = new Loja();
        loja.setId("loja-melhorias-teste");
        loja.setNome("Loja Teste Melhorias");
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
        // Depois do `loja.setEndereco(...)` e antes do save, adiciona:

loja.setHorariosFuncionamento(new HorariosFuncionamento(
        "10:00 - 22:00",   // domingo
        "10:00 - 22:00",   // segunda
        "10:00 - 22:00",   // terca
        "10:00 - 22:00",   // quarta
        "10:00 - 22:00",   // quinta
        "10:00 - 23:00",   // sexta
        "10:00 - 23:00"    // sabado
));

loja.setFormasPagamento(new FormasPagamento(
        true,   // aceitaDinheiro
        true,   // aceitaCredito
        true,   // aceitaDebito
        true,   // aceitaPix
        false,  // aceitaValeRefeicao
        false   // aceitaValeAlimentacao
));
        loja = lojaRepository.saveAndFlush(loja);

        funcionario = criarUsuario("func.melhorias@teste.com", Papel.FUNCIONARIO, loja.getId());

        produtoInativo = new Produto();
        produtoInativo.setId(UUID.randomUUID().toString());
        produtoInativo.setNome("Produto Desativado");
        produtoInativo.setPreco(new BigDecimal("15.00"));
        produtoInativo.setAtivo(false);
        produtoInativo.setCategoriaMenu("Bebidas");
        produtoInativo.setEstoque(100);
        produtoInativo.setLoja(loja);
        produtoInativo = produtoRepository.saveAndFlush(produtoInativo);

        // Inicializando os tokens
        tokenDono = tokenService.gerarToken(dono);
        tokenFuncionario = tokenService.gerarToken(funcionario);
        tokenOutroUsuario = tokenService.gerarToken(outroUsuario);
    }

  private Usuario criarUsuario(String email, Papel papel, String lojaVinculadaId) {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.randomUUID().toString());
    usuario.setNome("Usuario " + email);
    usuario.setEmail(email);
    usuario.setSenha("senha123");
    usuario.setTelefone(String.format("+55119999%05d", Math.floorMod(email.hashCode(), 100000)));
    usuario.setPapel(papel);
    usuario.setAtivo(true);
    usuario.setEmailVerificado(true); // Garante que o e-mail consta como verificado no teste
    usuario.setLojaVinculadaId(lojaVinculadaId);
    return usuarioRepository.saveAndFlush(usuario); // <-- Sincroniza imediatamente com o H2
}

  private RequestPostProcessor autenticarComo(Usuario usuario) {
    return request -> {
        org.springframework.security.core.context.SecurityContext context = 
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        usuario, null, usuario.getAuthorities()
                )
        );
        
        request.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return request;
    };

}

    // ---------- Abrir/fechar loja ----------

  @Test
void deveAbrirEFecharLoja() throws Exception {
    // Chamada 1 — DONO
    mockMvc.perform(patch("/api/v1/lojas/" + loja.getId() + "/abertura")
                    .header("Authorization", "Bearer " + tokenDono)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"isAberto\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAberto").value(false));

    // Chamada 2 — FUNCIONÁRIO (mesma loja, autorizado via loja_vinculada_id)
    mockMvc.perform(patch("/api/v1/lojas/" + loja.getId() + "/abertura")
                    .header("Authorization", "Bearer " + tokenFuncionario)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"isAberto\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAberto").value(true));
}

    @Test
    void naoDeveDeixarUsuarioSemAcessoAbrirOuFecharLoja() throws Exception {
        mockMvc.perform(patch("/api/v1/lojas/" + loja.getId() + "/abertura")
                        .header("Authorization", "Bearer " + tokenOutroUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAberto\": false}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Produto inativo (edição) ----------

    @Test
    void deveBuscarProdutoInativoParaEdicao() throws Exception {
        // GET /produtos/{id} público não retorna produto inativo
        mockMvc.perform(get("/api/v1/produtos/" + produtoInativo.getId()))
                .andExpect(status().isNotFound());

        // mas o endpoint do lojista retorna, mesmo inativo
        mockMvc.perform(get("/api/v1/lojista/produtos/" + produtoInativo.getId())
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Produto Desativado"))
                .andExpect(jsonPath("$.ativo").value(false));

        // funcionário da mesma loja também consegue
        mockMvc.perform(get("/api/v1/lojista/produtos/" + produtoInativo.getId())
                        .header("Authorization", "Bearer " + tokenFuncionario))
                .andExpect(status().isOk());

        // usuário sem acesso à loja não consegue (loja resolvida por LojaAccessService não bate)
        mockMvc.perform(get("/api/v1/lojista/produtos/" + produtoInativo.getId())
                        .header("Authorization", "Bearer " + tokenOutroUsuario))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
    }

    // ---------- Estoque ----------

    @Test
    void deveAtualizarEstoqueRapidamente() throws Exception {
        mockMvc.perform(patch("/api/v1/produtos/" + produtoInativo.getId() + "/estoque")
                        .header("Authorization", "Bearer " + tokenFuncionario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoque\": 25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(produtoInativo.getId()));

        mockMvc.perform(get("/api/v1/lojista/produtos/" + produtoInativo.getId())
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoque").value(25));
    }

    @Test
    void naoDeveAceitarEstoqueNegativo() throws Exception {
        mockMvc.perform(patch("/api/v1/produtos/" + produtoInativo.getId() + "/estoque")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoque\": -5}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Upload de imagem (modo mock) ----------

    @Test
    void deveAceitarUploadDeImagemValidaEmModoMock() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "logo.jpg", "image/jpeg", "conteudo-fake-de-imagem".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads/imagem")
                        .file(arquivo)
                        .param("pasta", "lojas")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").isNotEmpty())
                .andExpect(jsonPath("$.url", org.hamcrest.Matchers.startsWith("https://firebasestorage.googleapis.com")));
    }

    @Test
    void deveRecusarUploadDeTipoNaoSuportado() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "documento.pdf", "application/pdf", "conteudo".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads/imagem")
                        .file(arquivo)
                        .param("pasta", "produtos")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarUploadSemAutenticacao() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "logo.jpg", "image/jpeg", "conteudo".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads/imagem").file(arquivo).param("pasta", "lojas"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }

    // ---------- Preferências de notificação ----------

    @Test
    void deveLerEAtualizarPreferenciasDeNotificacao() throws Exception {
        // valores padrão
        mockMvc.perform(get("/api/v1/usuarios/" + dono.getId() + "/preferencias-notificacao")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificarNovoPedido").value(true))
                .andExpect(jsonPath("$.notificarAvaliacoes").value(false));

        // atualizar
        String novasPreferencias = "{\"notificarNovoPedido\":false,\"notificarMensagens\":false,\"notificarAvaliacoes\":true,\"notificarNovidades\":true}";
        mockMvc.perform(put("/api/v1/usuarios/" + dono.getId() + "/preferencias-notificacao")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novasPreferencias))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificarNovoPedido").value(false))
                .andExpect(jsonPath("$.notificarAvaliacoes").value(true));

        // persistiu de verdade
        mockMvc.perform(get("/api/v1/usuarios/" + dono.getId() + "/preferencias-notificacao")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificarNovoPedido").value(false));
    }

    @Test
    void naoDeveDeixarUsuarioVerPreferenciasDeOutraConta() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/" + dono.getId() + "/preferencias-notificacao")
                        .header("Authorization", "Bearer " + tokenOutroUsuario))
                .andExpect(status().isForbidden());
    }

    // ---------- E-mail duplicado ----------

    @Test
    void naoDeveTrocarParaEmailJaExistente() throws Exception {
        String payload = "{\"email\":\"" + dono.getEmail() + "\"}";
        mockMvc.perform(put("/api/v1/usuarios/" + outroUsuario.getId())
                        .header("Authorization", "Bearer " + tokenOutroUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
