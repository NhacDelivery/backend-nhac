package br.com.nhac.backend_nhac.infra.security;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    @Test
    void expiracaoRepresentaTrintaDiasExatosEmQualquerFuso() {
        var instante = java.time.Instant.parse("2030-01-01T00:00:00Z");
        var service = new TokenService(java.time.Clock.fixed(instante, java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "secret", "chave_secreta_de_teste_1234567890");
        var anterior = java.util.TimeZone.getDefault();
        try {
            for (String zona : java.util.List.of("UTC", "America/Sao_Paulo", "Asia/Tokyo")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zona));
                var token = com.auth0.jwt.JWT.decode(service.gerarToken(usuarioDeTeste()));
                assertEquals(instante.plus(java.time.Duration.ofDays(30)), token.getExpiresAtAsInstant());
            }
        } finally { java.util.TimeZone.setDefault(anterior); }
    }

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "chave_secreta_de_teste_1234567890");
    }

    private Usuario usuarioDeTeste() {
        Usuario usuario = new Usuario();
        usuario.setId("user_1");
        usuario.setNome("Matheus Alves");
        usuario.setEmail("matheus@nhac.com");
        return usuario;
    }

    @Test
    @DisplayName("Deve gerar um token JWT não nulo e não vazio para um usuário válido")
    void deveGerarTokenComSucesso() {
        String token = tokenService.gerarToken(usuarioDeTeste());

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Deve validar um token gerado e devolver o ID (subject) correto")
    void deveValidarTokenERecuperarId() {
        String token = tokenService.gerarToken(usuarioDeTeste());

        String idExtraido = tokenService.validarToken(token);

        assertEquals("user_1", idExtraido);
    }

    @Test
    @DisplayName("Deve retornar null ao validar um token inválido ou corrompido")
    void deveRetornarNullParaTokenInvalido() {
        String resultado = tokenService.validarToken("token.completamente.invalido");

        assertNull(resultado);
    }

    @Test
    @DisplayName("Deve retornar null ao validar um token assinado com outra chave secreta")
    void deveRetornarNullParaTokenAssinadoComOutraChave() {
        String token = tokenService.gerarToken(usuarioDeTeste());

        TokenService outroServico = new TokenService();
        ReflectionTestUtils.setField(outroServico, "secret", "outra_chave_completamente_diferente");

        assertNull(outroServico.validarToken(token));
    }
}
