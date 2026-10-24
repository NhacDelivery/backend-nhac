package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.domain.auth.dto.ChecarEmailRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ChecarEmailResponseDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ConfirmarEmailDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginResponseDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.SocialLoginRequestDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.CredenciaisInvalidasException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.domain.auth.GoogleAuthService;
import br.com.nhac.backend_nhac.domain.auth.SmsAuthService;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints para Login, Registro e Emissão de Tokens JWT")
public class AuthController {
    public record ConfirmacaoCadastroDTO(String tokenCadastro) {}

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final GoogleAuthService googleAuthService;
    private final SmsAuthService smsAuthService;
    private final br.com.nhac.backend_nhac.domain.auth.VerificacaoEmailService verificacaoEmailService;
    private final UsuarioService usuarioService;
    private final CadastroService cadastroService;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, TokenService tokenService, GoogleAuthService googleAuthService, SmsAuthService smsAuthService, VerificacaoEmailService verificacaoEmailService, UsuarioService usuarioService, CadastroService cadastroService) {


        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.googleAuthService = googleAuthService;
        this.smsAuthService = smsAuthService;
        this.verificacaoEmailService = verificacaoEmailService;
        this.usuarioService = usuarioService;
        this.cadastroService = cadastroService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody @Valid LoginRequestDTO body,
            @RequestHeader(value = "X-App-Origin", required = false) String appOrigin) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(body.email())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail não encontrado ou senha inválida."));

        if (!usuario.isEmailVerificado()) {
            throw new RegraDeNegocioException("Verifique seu e-mail antes de fazer login.");
        }

        if (!usuario.isAtivo()) {
            throw new RegraDeNegocioException("Esta conta está desativada. Fale com o dono da loja para reativá-la.");
        }

        if (passwordEncoder.matches(body.senha(), usuario.getSenha())) {
            // Checagem de origem SÓ depois de confirmar a senha — assim uma
            // tentativa com senha errada continua devolvendo a mesma
            // mensagem genérica de sempre, sem vazar se aquele e-mail é de
            // uma conta LOJISTA/FUNCIONARIO só pelo fato de mandar o header
            // do app do motoboy.
            validarOrigemApp(usuario, appOrigin);
            String token = tokenService.gerarToken(usuario);
            return ResponseEntity.ok(LoginResponseDTO.from(usuario, token, false));
        }

        throw new CredenciaisInvalidasException("E-mail não encontrado ou senha inválida.");
    }

    /**
     * Bloqueia contas LOJISTA/FUNCIONARIO no app do motoboy. O header
     * X-App-Origin é enviado pelo cliente (opcional, ausente = comportamento
     * de sempre); "motoboy" é o único valor que ativa a checagem, então os
     * apps de cliente e do lojista continuam funcionando sem qualquer
     * mudança mesmo que ainda não mandem o header.
     *
     * CLIENTE passa normalmente mesmo sem cadastro de entregador ainda — o
     * próprio app do motoboy decide, depois do login, se manda a pessoa pro
     * fluxo de completar o cadastro (POST /entregador/cadastro).
     */
    private void validarOrigemApp(Usuario usuario, String appOrigin) {
        if (!"motoboy".equalsIgnoreCase(appOrigin)) {
            return;
        }
        if (usuario.getPapel() == Papel.LOJISTA || usuario.getPapel() == Papel.FUNCIONARIO) {
            throw new AcessoNegadoException("Esta conta é de loja e não pode ser usada no app do motoboy.");
        }
    }

    @Operation(summary = "Enviar código de verificação para cadastro", description = "Envia um código de 6 dígitos para o e-mail informado. Necessário para confirmar o e-mail antes do registro.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Código enviado com sucesso"),
            @ApiResponse(responseCode = "400", description = "E-mail inválido, não informado ou já em uso"),
            @ApiResponse(responseCode = "429", description = "Rate limit excedido")
    })
    @PostMapping("/enviar-codigo-cadastro")
    public ResponseEntity<Void> enviarCodigoCadastro(@RequestBody @Valid ChecarEmailRequestDTO body) {
        verificacaoEmailService.enviarCodigoCadastro(body.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Confirmar e-mail para cadastro", description = "Valida o OTP e retorna tokenCadastro. Envie essa prova na conclusão do registro; vale por 30 minutos e só pode ser consumida uma vez.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "E-mail confirmado; resposta contém tokenCadastro"),
            @ApiResponse(responseCode = "400", description = "Código inválido, expirado ou limite de erros excedido"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido")
    })
    @PostMapping("/confirmar-email-cadastro")
    public ResponseEntity<ConfirmacaoCadastroDTO> confirmarEmailCadastro(@RequestBody @Valid ConfirmarEmailDTO body) {
        String token = verificacaoEmailService.verificarCodigoCadastro(body.email(), body.codigo());
        return ResponseEntity.ok(new ConfirmacaoCadastroDTO(token));
    }

    @Operation(summary = "Concluir cadastro", description = "Insere uma nova conta consumindo tokenCadastro do e-mail confirmado. O ID legado recebido é ignorado: adote usuarioId e token da resposta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada com identidade gerada pelo servidor"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, contato já em uso ou prova de cadastro inválida"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido")
    })
    @PostMapping("/registrar")
    public ResponseEntity<LoginResponseDTO> registrar(@RequestBody @Valid RegistroRequestDTO body) {
        Usuario novoUsuario = cadastroService.registrar(body);

        String token = tokenService.gerarToken(novoUsuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(LoginResponseDTO.from(novoUsuario, token, false));
    }

    @PostMapping("/social")
    public ResponseEntity<LoginResponseDTO> loginSocial(
            @RequestBody @Valid SocialLoginRequestDTO dto,
            @RequestHeader(value = "X-App-Origin", required = false) String appOrigin) {
        LoginResponseDTO response = googleAuthService.autenticarComGoogle(dto.idToken());
        // O Google pode logar automaticamente uma conta já existente (é
        // exatamente o caso relatado: alguém com conta de loja tenta entrar
        // no app do motoboy e o Google loga sem pedir confirmação) — por
        // isso a mesma checagem de origem entra aqui também, buscando o
        // usuário de novo pelo id que já veio pronto na resposta.
        Usuario usuario = usuarioRepository.findById(response.usuarioId())
                .orElseThrow(() -> new CredenciaisInvalidasException("Não foi possível autenticar com o Google."));
        validarOrigemApp(usuario, appOrigin);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login-sms")
    @Operation(summary = "Realiza login via SMS (Passwordless)", description = "Valida o OTP. Se o telefone não existir, cadastra um novo usuário de forma invisível.")
    public ResponseEntity<LoginResponseDTO> loginSms(
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO dto,
            @RequestHeader(value = "X-App-Origin", required = false) String appOrigin) {
        LoginResponseDTO response = smsAuthService.autenticarComSms(dto);
        Usuario usuario = usuarioRepository.findById(response.usuarioId())
                .orElseThrow(() -> new CredenciaisInvalidasException("Não foi possível autenticar via SMS."));
        validarOrigemApp(usuario, appOrigin);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verifica se um e-mail já existe", description = "Retorna se o e-mail informado já está cadastrado no sistema. Útil para direcionar o usuário para o fluxo de login ou de cadastro.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificação realizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "E-mail inválido ou não informado")
    })
    @PostMapping("/checar-email")
    public ResponseEntity<ChecarEmailResponseDTO> checarEmail(@RequestBody @Valid ChecarEmailRequestDTO body) {
        boolean existe = usuarioRepository.findByEmailIgnoreCase(body.email()).isPresent();
        return ResponseEntity.ok(new ChecarEmailResponseDTO(existe));
    }


    @Operation(summary = "Solicitar redefinição de senha por e-mail", description = "Envia um e-mail com código para redefinição caso o e-mail exista.")
    @PostMapping("/esqueci-senha/email")
    public ResponseEntity<Void> esqueciSenhaEmail(
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.auth.dto.EsqueciSenhaEmailDTO dto) {
        
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(dto.email().trim())
                .orElseThrow(() -> new br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException("Nenhum usuário encontrado com este e-mail."));

        if (!usuario.isAtivo()) {
            throw new RegraDeNegocioException("Usuário inativo.");
        }

        verificacaoEmailService.enviarCodigoReset(dto.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Validar código de redefinição de senha", description = "Confere o código sem consumi-lo. A redefinição verifica novamente e consome o código.")
    @PostMapping("/validar-codigo-redefinicao/email")
    public ResponseEntity<Void> validarCodigoRedefinicaoEmail(
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoRedefinicaoEmailDTO dto) {
        verificacaoEmailService.validarCodigoReset(dto.email(), dto.codigo());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Concluir redefinição de senha por e-mail", description = "Valida o código do e-mail e atualiza a senha do usuário.")
    @PostMapping("/redefinir-senha/email")
    public ResponseEntity<Void> redefinirSenhaEmail(
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.auth.dto.RedefinirSenhaEmailDTO dto) {

        verificacaoEmailService.verificarCodigoValido(dto.email(), dto.codigo());
        usuarioService.atualizarSenhaPorEmail(dto.email(), dto.novaSenha());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Alterar senha", description = "Altera a senha do usuário logado validando a senha atual.")
    @PutMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Usuario usuarioLogado,
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.auth.dto.AlterarSenhaDTO dto) {

        if (usuarioLogado.getSenha() == null) {
            throw new RegraDeNegocioException("Esta conta não possui senha cadastrada. Ela foi criada via login por telefone.");
        }

        if (!passwordEncoder.matches(dto.senhaAtual(), usuarioLogado.getSenha())) {
            throw new br.com.nhac.backend_nhac.exceptions.CredenciaisInvalidasException("A senha atual informada está incorreta.");
        }

        usuarioLogado.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuarioLogado);

        return ResponseEntity.ok().build();
    }
}
