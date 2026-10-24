package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroSegurancaIT extends AbstractIntegrationTest {
    @Autowired CadastroService cadastro;
    @Autowired UsuarioRepository usuarios;
    @Autowired VerificacaoEmailService verificacao;
    @MockitoBean EmailService emailService;

    private RegistroRequestDTO dados(String id, String email, String token) {
        return new RegistroRequestDTO(id, "Conta nova", email, "11911112222", "Senha12345", token);
    }

    @Test
    void confirmacaoHttpEntregaComprovanteParaConcluirCadastro() throws Exception {
        codigoVerificacaoEmailRepository.saveAndFlush(CodigoVerificacaoEmail.builder()
                .email("http@teste.com").codigo("123456").utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.CADASTRO)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15)).build());
        var resposta = mockMvc.perform(post("/api/v1/auth/confirmar-email-cadastro")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"http@teste.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        String prova = objectMapper.readTree(resposta.getResponse().getContentAsString()).get("tokenCadastro").asText();
        assertEquals(43, prova.length());
        mockMvc.perform(post("/api/v1/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dados("id-fornecido", "http@teste.com", prova))))
                .andExpect(status().isCreated());
        assertFalse(usuarios.existsById("id-fornecido"));
        assertEquals(1, usuarios.count());
    }

    @Test
    void idRecebidoNuncaSubstituiUmaContaExistente() {
        var original = new Usuario(); original.setId("conta-existente"); original.setNome("Original");
        original.setEmail("original@teste.com"); original.setSenha("hash-original");
        usuarios.saveAndFlush(original);
        String token = criarCodigoVerificadoPara("nova@teste.com");
        var nova = cadastro.registrar(dados(original.getId(), "nova@teste.com", token));
        assertNotEquals(original.getId(), nova.getId());
        var preservada = usuarios.findById(original.getId()).orElseThrow();
        assertEquals("Original", preservada.getNome());
        assertEquals("hash-original", preservada.getSenha());
        assertEquals(2, usuarios.count());
    }

    @Test
    void invalidarCodigoPorReenvioNaoAutorizaCadastro() {
        codigoVerificacaoEmailRepository.saveAndFlush(CodigoVerificacaoEmail.builder()
                .email("nova@teste.com").codigo("123456").utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.CADASTRO)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15)).build());
        verificacao.salvarNovoCodigoCadastro("nova@teste.com");
        assertThrows(RegraDeNegocioException.class, () -> cadastro.registrar(dados("id", "nova@teste.com", "123456")));
        assertEquals(0, usuarios.count());
    }

    @Test
    void provaPertenceAoEmailERecebeConsumoUnico() {
        String token = criarCodigoVerificadoPara("nova@teste.com");
        assertThrows(RegraDeNegocioException.class, () -> cadastro.registrar(dados("id", "outra@teste.com", token)));
        cadastro.registrar(dados("id", "nova@teste.com", token));
        assertThrows(RegraDeNegocioException.class, () -> cadastro.registrar(dados("id", "nova@teste.com", token)));
        assertEquals(1, usuarios.count());
    }

    @Test
    void errosDeOtpSaoPersistidosEImpedemConfirmacaoPosterior() {
        var codigo = codigoVerificacaoEmailRepository.saveAndFlush(CodigoVerificacaoEmail.builder()
                .email("nova@teste.com").codigo("123456").utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.CADASTRO)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15)).build());
        for (int i = 0; i < 3; i++) assertThrows(RegraDeNegocioException.class,
                () -> verificacao.verificarCodigoCadastro("nova@teste.com", "000000"));
        assertEquals(3, codigoVerificacaoEmailRepository.findById(codigo.getId()).orElseThrow().getTentativas());
        assertThrows(RegraDeNegocioException.class, () -> verificacao.verificarCodigoCadastro("nova@teste.com", "123456"));
    }

    @Test
    void loginTemLimiteRealNaEntradaHttp() throws Exception {
        for (int i = 0; i < 10; i++) mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ausente@teste.com\",\"senha\":\"Senha12345\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.8")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ausente@teste.com\",\"senha\":\"Senha12345\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
