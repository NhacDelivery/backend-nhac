package br.com.nhac.backend_nhac.domain.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.com.nhac.backend_nhac.domain.auth.dto.ChecarEmailRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ChecarEmailResponseDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginResponseDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.SocialLoginRequestDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.CredenciaisInvalidasException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.infra.security.TokenService;


@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CodigoVerificacaoEmailRepository codigoVerificacaoEmailRepository;

    private MockMvc mockMvc;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();


    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private SmsAuthService smsAuthService;

    @Mock
    private br.com.nhac.backend_nhac.domain.auth.VerificacaoEmailService verificacaoEmailService;

    @InjectMocks
    private AuthController authController;

    @Test
    void deveValidarCodigoSemRedefinirSenha() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validar-codigo-redefinicao/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@exemplo.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk());
        verify(verificacaoEmailService).validarCodigoReset("cliente@exemplo.com", "123456");
        verify(verificacaoEmailService, never()).verificarCodigoValido(any(), any());
    }

    @Test
    void deveRejeitarFormatoInvalidoDoCodigo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validar-codigo-redefinicao/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@exemplo.com\",\"codigo\":\"abc123\"}"))
                .andExpect(status().isBadRequest());
        verify(verificacaoEmailService, never()).validarCodigoReset(any(), any());
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("Deve retornar 200 e o token JWT ao realizar login por SMS com sucesso")
    void deveAutenticarComSmsComSucesso() throws Exception {
        br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO requisicao = new br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO("+5511999999999", "123456", null);

        LoginResponseDTO respostaEsperada = new LoginResponseDTO("jwt_gerado_pelo_backend_sms", "user_novo", "Novo Usuário", true, "CLIENTE", null);

        when(smsAuthService.autenticarComSms(requisicao)).thenReturn(respostaEsperada);

        // Rebusca o usuário para checar a origem do app (header X-App-Origin).
        Usuario usuarioAutenticado = new Usuario();
        usuarioAutenticado.setId("user_novo");
        usuarioAutenticado.setNome("Novo Usuário");
        usuarioAutenticado.setPapel(Papel.CLIENTE);
        when(usuarioRepository.findById("user_novo")).thenReturn(Optional.of(usuarioAutenticado));

        mockMvc.perform(post("/api/v1/auth/login-sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_gerado_pelo_backend_sms"))
                .andExpect(jsonPath("$.usuarioId").value("user_novo"))
                .andExpect(jsonPath("$.isNovoUsuario").value(true));
    }

    @Test
    @DisplayName("Deve lançar CredenciaisInvalidasException (Erro 401) quando a senha estiver incorreta")
    void deveLancarExcecaoQuandoSenhaIncorreta() {
        LoginRequestDTO requisicao = new LoginRequestDTO("matheus@nhac.com", "senha_errada");

        Usuario usuarioDoBanco = new Usuario();
        usuarioDoBanco.setEmail("matheus@nhac.com");
        usuarioDoBanco.setSenha("hash_da_senha_correta");
        usuarioDoBanco.setEmailVerificado(true);

        when(usuarioRepository.findByEmailIgnoreCase("matheus@nhac.com")).thenReturn(Optional.of(usuarioDoBanco));

        when(passwordEncoder.matches("senha_errada", "hash_da_senha_correta")).thenReturn(false);

        assertThrows(CredenciaisInvalidasException.class, () -> {
            authController.login(requisicao, null);
        });

        verify(tokenService, never()).gerarToken(any());
    }

    @Test
    @DisplayName("Deve efetuar login com sucesso e devolver o token quando as credenciais forem válidas")
    void deveEfetuarLoginComSucesso() {
        LoginRequestDTO requisicao = new LoginRequestDTO("matheus@nhac.com", "senha_correta");

        Usuario usuarioDoBanco = new Usuario();
        usuarioDoBanco.setId("user_1");
        usuarioDoBanco.setNome("Matheus Alves");
        usuarioDoBanco.setEmail("matheus@nhac.com");
        usuarioDoBanco.setSenha("hash_da_senha_correta");
        usuarioDoBanco.setEmailVerificado(true);

        when(usuarioRepository.findByEmailIgnoreCase("matheus@nhac.com")).thenReturn(Optional.of(usuarioDoBanco));
        when(passwordEncoder.matches("senha_correta", "hash_da_senha_correta")).thenReturn(true);
        when(tokenService.gerarToken(usuarioDoBanco)).thenReturn("token_jwt_gerado");

        ResponseEntity<LoginResponseDTO> resposta = authController.login(requisicao, null);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("token_jwt_gerado", resposta.getBody().token());
        assertEquals("user_1", resposta.getBody().usuarioId());
        assertEquals("CLIENTE", resposta.getBody().papel());
    }

    @Test
    @DisplayName("Deve bloquear login por senha de conta de loja no app do motoboy (X-App-Origin: motoboy)")
    void deveBloquearContaDeLojaNoAppDoMotoboy() {
        LoginRequestDTO requisicao = new LoginRequestDTO("loja@nhac.com", "senha_correta");

        Usuario lojista = new Usuario();
        lojista.setId("user_loja");
        lojista.setEmail("loja@nhac.com");
        lojista.setSenha("hash_da_senha_correta");
        lojista.setEmailVerificado(true);
        lojista.setAtivo(true);
        lojista.setPapel(Papel.LOJISTA);

        when(usuarioRepository.findByEmailIgnoreCase("loja@nhac.com")).thenReturn(Optional.of(lojista));
        when(passwordEncoder.matches("senha_correta", "hash_da_senha_correta")).thenReturn(true);

        assertThrows(AcessoNegadoException.class, () -> authController.login(requisicao, "motoboy"));

        verify(tokenService, never()).gerarToken(any());
    }

    @Test
    @DisplayName("Deve manter a mensagem genérica ao errar a senha, mesmo vindo do app do motoboy")
    void deveManterMensagemGenericaComSenhaErradaNoAppDoMotoboy() {
        LoginRequestDTO requisicao = new LoginRequestDTO("loja@nhac.com", "senha_errada");

        Usuario lojista = new Usuario();
        lojista.setId("user_loja");
        lojista.setEmail("loja@nhac.com");
        lojista.setSenha("hash_da_senha_correta");
        lojista.setEmailVerificado(true);
        lojista.setAtivo(true);
        lojista.setPapel(Papel.LOJISTA);

        when(usuarioRepository.findByEmailIgnoreCase("loja@nhac.com")).thenReturn(Optional.of(lojista));
        when(passwordEncoder.matches("senha_errada", "hash_da_senha_correta")).thenReturn(false);

        // A checagem de origem só roda depois de validar a senha: quem erra a
        // senha não descobre se aquele e-mail pertence a uma conta de loja.
        assertThrows(CredenciaisInvalidasException.class, () -> authController.login(requisicao, "motoboy"));
    }

    @Test
    @DisplayName("Deve permitir login social de conta de loja quando o header X-App-Origin estiver ausente")
    void devePermitirLoginSocialSemHeaderDeOrigem() {
        SocialLoginRequestDTO requisicao = new SocialLoginRequestDTO("token_google_loja");
        LoginResponseDTO respostaEsperada = new LoginResponseDTO("jwt_loja", "user_loja", "Dona da Loja", false, "LOJISTA", null);

        Usuario lojista = new Usuario();
        lojista.setId("user_loja");
        lojista.setPapel(Papel.LOJISTA);

        when(googleAuthService.autenticarComGoogle("token_google_loja")).thenReturn(respostaEsperada);
        when(usuarioRepository.findById("user_loja")).thenReturn(Optional.of(lojista));

        ResponseEntity<LoginResponseDTO> resposta = authController.loginSocial(requisicao, null);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("jwt_loja", resposta.getBody().token());
    }

    @Test
    @DisplayName("Deve lançar CredenciaisInvalidasException quando o e-mail não for encontrado")
    void deveLancarExcecaoQuandoEmailNaoEncontrado() {
        LoginRequestDTO requisicao = new LoginRequestDTO("fantasma@nhac.com", "qualquer_senha");

        when(usuarioRepository.findByEmailIgnoreCase("fantasma@nhac.com")).thenReturn(Optional.empty());

        assertThrows(CredenciaisInvalidasException.class, () -> authController.login(requisicao, null));

        verify(tokenService, never()).gerarToken(any());
    }

    @Test
    @DisplayName("Deve registrar um novo usuário com sucesso quando o e-mail ainda não estiver em uso")
    void deveRegistrarUsuarioComSucesso() {
        RegistroRequestDTO requisicao = new RegistroRequestDTO(
                "user_novo", "Novo Usuário", "novo@nhac.com", "11999998888", "senha123");

        when(usuarioRepository.findByEmailIgnoreCase("novo@nhac.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("senha_encriptada");
        when(tokenService.gerarToken(any(Usuario.class))).thenReturn("token_jwt_gerado");
        
        // Mock do código de verificação de cadastro
        var codigoVerificacao = new br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoEmail();
        codigoVerificacao.setUtilizado(true);
        when(codigoVerificacaoEmailRepository.findTopByEmailAndTipoAndUtilizadoTrueAndCriadoEmGreaterThanEqualOrderByCriadoEmDesc(
            eq("novo@nhac.com"), 
            eq(CodigoVerificacaoEmail.TipoCodigo.CADASTRO),
            any(java.time.LocalDateTime.class)
        )).thenReturn(Optional.of(codigoVerificacao));

        ResponseEntity<LoginResponseDTO> resposta = authController.registrar(requisicao);

        assertEquals(HttpStatus.CREATED, resposta.getStatusCode());
        assertEquals("token_jwt_gerado", resposta.getBody().token());
        assertEquals("CLIENTE", resposta.getBody().papel());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("novo@nhac.com", captor.getValue().getEmail());
        assertEquals("senha_encriptada", captor.getValue().getSenha());
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar registrar um e-mail já em uso")
    void deveLancarExcecaoAoRegistrarEmailDuplicado() {
        RegistroRequestDTO requisicao = new RegistroRequestDTO(
                "user_novo", "Novo Usuário", "matheus@nhac.com", "11999998888", "senha123");

        Usuario usuarioExistente = new Usuario();
        usuarioExistente.setEmail("matheus@nhac.com");
        usuarioExistente.setEmailVerificado(true);

        // Mock do código de verificação como já utilizado (para passar pela validação de email)
        CodigoVerificacaoEmail codigoVerificacao = new CodigoVerificacaoEmail();
        codigoVerificacao.setEmail("matheus@nhac.com");
        codigoVerificacao.setTipo(CodigoVerificacaoEmail.TipoCodigo.CADASTRO);
        codigoVerificacao.setUtilizado(true);
        codigoVerificacao.setCriadoEm(LocalDateTime.now());

        when(codigoVerificacaoEmailRepository.findTopByEmailAndTipoAndUtilizadoTrueAndCriadoEmGreaterThanEqualOrderByCriadoEmDesc(
                eq("matheus@nhac.com"), 
                eq(CodigoVerificacaoEmail.TipoCodigo.CADASTRO), 
                any(LocalDateTime.class)))
                .thenReturn(Optional.of(codigoVerificacao));

        when(usuarioRepository.findByEmailIgnoreCase("matheus@nhac.com"))
                .thenReturn(Optional.of(usuarioExistente));

        assertThrows(RegraDeNegocioException.class, () -> authController.registrar(requisicao));

        verify(usuarioRepository, never()).save(any());
        verify(tokenService, never()).gerarToken(any());
    }

    @Test
    @DisplayName("Deve retornar 200 e o token JWT ao receber um ID Token válido do Google")
    void deveAutenticarComGoogleComSucesso() throws Exception {
        SocialLoginRequestDTO requisicao = new SocialLoginRequestDTO("token_google_falso_mas_mockado");

        LoginResponseDTO respostaEsperada = new LoginResponseDTO("jwt_gerado_pelo_backend", "user_1", "Usuário Nhac", false, "CLIENTE", null);

        when(googleAuthService.autenticarComGoogle("token_google_falso_mas_mockado")).thenReturn(respostaEsperada);

        Usuario usuarioGoogle = new Usuario();
        usuarioGoogle.setId("user_1");
        usuarioGoogle.setNome("Usuário Nhac");
        usuarioGoogle.setPapel(Papel.CLIENTE);
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioGoogle));

        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_gerado_pelo_backend"))
                .andExpect(jsonPath("$.usuarioId").value("user_1"));
    }

    @Test
    @DisplayName("Deve retornar existe true quando o e-mail já estiver cadastrado")
    void deveRetornarExisteTrueQuandoEmailCadastrado() {
        ChecarEmailRequestDTO requisicao = new ChecarEmailRequestDTO("existente@nhac.com");

        when(usuarioRepository.findByEmailIgnoreCase("existente@nhac.com")).thenReturn(Optional.of(new Usuario()));

        ResponseEntity<ChecarEmailResponseDTO> resposta = authController.checarEmail(requisicao);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertTrue(resposta.getBody().existe());
    }

    @Test
    @DisplayName("Deve retornar existe false quando o e-mail não estiver cadastrado")
    void deveRetornarExisteFalseQuandoEmailNaoCadastrado() {
        ChecarEmailRequestDTO requisicao = new ChecarEmailRequestDTO("novo@nhac.com");

        when(usuarioRepository.findByEmailIgnoreCase("novo@nhac.com")).thenReturn(Optional.empty());

        ResponseEntity<ChecarEmailResponseDTO> resposta = authController.checarEmail(requisicao);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertFalse(resposta.getBody().existe());
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar alterar senha de uma conta criada por telefone (sem senha prévia)")
    void deveLancarExcecaoAoAlterarSenhaDeUsuarioSms() {
        Usuario usuarioSms = new Usuario();
        usuarioSms.setSenha(null);

        br.com.nhac.backend_nhac.domain.auth.dto.AlterarSenhaDTO requisicao = 
                new br.com.nhac.backend_nhac.domain.auth.dto.AlterarSenhaDTO("senha_qualquer", "nova_senha_forte");

        RegraDeNegocioException excecao = assertThrows(RegraDeNegocioException.class, () -> {
            authController.alterarSenha(usuarioSms, requisicao);
        });

        assertEquals("Esta conta não possui senha cadastrada. Ela foi criada via login por telefone.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException ao tentar recuperar senha de um e-mail não cadastrado (ex: usuário de telefone)")
    void deveLancarExcecaoAoRecuperarSenhaPorEmailNaoCadastrado() {
        br.com.nhac.backend_nhac.domain.auth.dto.EsqueciSenhaEmailDTO requisicao = 
                new br.com.nhac.backend_nhac.domain.auth.dto.EsqueciSenhaEmailDTO("naoexiste@nhac.com");

        when(usuarioRepository.findByEmailIgnoreCase("naoexiste@nhac.com")).thenReturn(Optional.empty());

        br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException excecao = assertThrows(
                br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException.class, 
                () -> authController.esqueciSenhaEmail(requisicao)
        );

        assertEquals("Nenhum usuário encontrado com este e-mail.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve retornar 200 OK e chamar o serviço de e-mail ao recuperar senha de um e-mail válido")
    void deveEnviarCodigoResetComSucesso() {
        br.com.nhac.backend_nhac.domain.auth.dto.EsqueciSenhaEmailDTO requisicao = 
                new br.com.nhac.backend_nhac.domain.auth.dto.EsqueciSenhaEmailDTO("matheus@nhac.com");

        Usuario usuarioValido = new Usuario();
        usuarioValido.setEmail("matheus@nhac.com");
        usuarioValido.setAtivo(true); // O usuário precisa estar ativo

        when(usuarioRepository.findByEmailIgnoreCase("matheus@nhac.com")).thenReturn(Optional.of(usuarioValido));

        ResponseEntity<Void> resposta = authController.esqueciSenhaEmail(requisicao);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        verify(verificacaoEmailService, times(1)).enviarCodigoReset("matheus@nhac.com");
    }
}
