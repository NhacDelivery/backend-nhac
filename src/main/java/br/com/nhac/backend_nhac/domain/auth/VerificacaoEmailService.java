package br.com.nhac.backend_nhac.domain.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerificacaoEmailService {

    private final CodigoVerificacaoEmailRepository codigoRepository;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepository;

    private static final int TEMPO_EXPIRACAO_MINUTOS = 15;
    private static final int MAX_TENTATIVAS = 3;
    private static final String URL_LOGO_NHAC = "https://raw.githubusercontent.com/NhacDelivery/backend-nhac/main/nhac-logo.png";

    @Transactional
    public void enviarCodigoCadastro(String email) {
        String finalEmail = email.trim().toLowerCase(Locale.ROOT);
        
        // Verifica se e-mail já está cadastrado e ativo
        if (usuarioRepository.findByEmailIgnoreCase(finalEmail).isPresent()) {
            throw new RegraDeNegocioException("Este e-mail já está em uso.");
        }
        
        CodigoVerificacaoEmail novoCodigo = salvarNovoCodigoCadastro(finalEmail);
        enviarEmailCadastro(finalEmail, novoCodigo);
    }

    @Transactional
    public CodigoVerificacaoEmail salvarNovoCodigoCadastro(String email) {
        LocalDateTime agora = LocalDateTime.now();
        codigoRepository.inativarCodigosAtivosPorEmail(email);

        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        CodigoVerificacaoEmail novoCodigo = CodigoVerificacaoEmail.builder()
                .email(email)
                .codigo(codigo)
                .dataExpiracao(agora.plusMinutes(TEMPO_EXPIRACAO_MINUTOS))
                .tentativas(0)
                .utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.CADASTRO)
                .build();

        return codigoRepository.save(novoCodigo);
    }

    private void enviarEmailCadastro(String email, CodigoVerificacaoEmail novoCodigo) {
        String codigo = novoCodigo.getCodigo();


        String assunto = "Confirme seu e-mail - Nhac Delivery";
        
        String htmlConteudo = """
                <!DOCTYPE html>
                <html lang="pt-BR" xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirme seu e-mail · Nhac</title>
                <style>
                body, table, td { font-family: 'Roboto', Arial, Helvetica, sans-serif; }
                body { margin:0; padding:0; background-color:#FFE7E5; }
                .email-container { max-width:600px; margin: 0 auto; background-color:#FFE7E5; }
                .header { background-color:#FFE7E5; padding: 25px 30px; }
                .header img { max-height: 50px; display: block; }
                .sub-header { background-color:#FF6961; padding: 15px 30px; font-size:16px; font-weight:700; color:#FFFFFF; }
                .content { padding: 40px 30px; color: #333333; font-size: 15px; line-height: 1.6; }
                .greeting { font-size: 18px; font-weight: 700; color: #FF6961; margin-bottom: 25px; }
                .highlight { background-color: #FCDABB; padding: 2px 4px; border-radius: 4px; font-weight: bold; }
                .code-box { text-align: center; margin: 40px 0; }
                .code { font-size: 44px; font-weight: 700; color: #FF6961; letter-spacing: 4px; }
                </style>
                </head>
                <body style="background-color:#FFE7E5; margin:0; padding:0;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FFE7E5;">
                <tr>
                <td align="center">
                <table role="presentation" class="email-container" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px; max-width:600px; text-align: left;">
                <tr>
                <td class="header" style="background-color:#FFE7E5; padding:25px 30px;">
                  <img src="%s" alt="Nhac" style="max-height:50px; display:block;" />
                </td>
                </tr>
                <tr>
                <td class="sub-header" style="background-color:#FF6961; padding:15px 30px; font-size:16px; font-weight:700; color:#FFFFFF;">
                  Esse é seu código de acesso
                </td>
                </tr>
                <tr>
                <td class="content" style="padding:40px 30px; color:#333333; font-size:15px; line-height:1.6;">
                  <div class="greeting" style="font-size:18px; font-weight:700; color:#FF6961; margin-bottom:25px;">
                    Olá!
                  </div>
                  
                  <div style="margin-bottom:20px;">
                    Para validarmos o acesso ao seu aplicativo com segurança, <strong>use o código abaixo diretamente no seu app do <span class="highlight" style="background-color:#FCDABB; padding:2px 4px; border-radius:4px; font-weight:bold;">Nhac</span></strong>.
                  </div>
                  
                  <div class="code-box" style="text-align:center; margin:40px 0;">
                    <span class="code" style="font-size:44px; font-weight:700; color:#FF6961; letter-spacing:4px;">%s</span>
                  </div>
                  
                  <div style="margin-bottom:20px; text-align: center; color: #8A8A8A; font-size: 14px;">
                    Este código expira em <strong>%d minutos</strong>.
                  </div>
                </td>
                </tr>
                </table>
                </td>
                </tr>
                </table>
                </body>
                </html>
                """.formatted(URL_LOGO_NHAC, codigo, TEMPO_EXPIRACAO_MINUTOS);
        emailService.enviarEmailHtml(email, assunto, htmlConteudo);
    }

    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public String verificarCodigoCadastro(String email, String codigoDigitado) {
        String emailFormatado = email.trim().toLowerCase(Locale.ROOT);
        LocalDateTime agora = LocalDateTime.now();

        CodigoVerificacaoEmail codigoBanco = codigoRepository
            .findTopByEmailAndTipoAndUtilizadoFalseAndDataExpiracaoAfterOrderByCriadoEmDesc(
                emailFormatado, 
                CodigoVerificacaoEmail.TipoCodigo.CADASTRO, 
                agora
            )
            .orElseThrow(() -> new RegraDeNegocioException("Código de verificação inválido ou expirado."));

        if (codigoBanco.getTentativas() >= MAX_TENTATIVAS) {
            codigoBanco.setUtilizado(true);
            throw new RegraDeNegocioException("Limite de tentativas excedido. Solicite um novo código.");
        }
        if (!codigoBanco.getCodigo().equals(codigoDigitado.trim())) {
            codigoBanco.setTentativas(codigoBanco.getTentativas() + 1);
            codigoRepository.save(codigoBanco);
            throw new RegraDeNegocioException("Código de verificação inválido ou expirado.");
        }

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codigoBanco.setUtilizado(true);
        codigoBanco.setValidadoEm(agora);
        codigoBanco.setTokenCadastroHash(hashToken(token));
        codigoRepository.save(codigoBanco);
        return token;
    }

    static String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }


    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public void verificarCodigoValido(String email, String codigoDigitado) {
        CodigoVerificacaoEmail registro = conferirCodigoReset(email, codigoDigitado);
        registro.setUtilizado(true);
        codigoRepository.save(registro);
    }

    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public void validarCodigoReset(String email, String codigoDigitado) {
        conferirCodigoReset(email, codigoDigitado);
    }

    private CodigoVerificacaoEmail conferirCodigoReset(String email, String codigoDigitado) {
        email = email.trim().toLowerCase(Locale.ROOT);
        LocalDateTime agora = LocalDateTime.now();

        CodigoVerificacaoEmail registro = codigoRepository
                .findTopByEmailAndTipoAndUtilizadoFalseAndDataExpiracaoAfterOrderByCriadoEmDesc(
                        email, CodigoVerificacaoEmail.TipoCodigo.RESET_SENHA, agora)
                .orElseThrow(() -> new RegraDeNegocioException("Código expirado ou não encontrado. Solicite um novo código."));

        if (registro.getTentativas() >= MAX_TENTATIVAS) {
            registro.setUtilizado(true);
            codigoRepository.save(registro);
            throw new RegraDeNegocioException("Limite de tentativas excedido para este código. Solicite um novo.");
        }

        if (!registro.getCodigo().equals(codigoDigitado.trim())) {
            registro.setTentativas(registro.getTentativas() + 1);
            codigoRepository.save(registro);
            throw new RegraDeNegocioException("Código de verificação inválido.");
        }

        return registro;
    }

    @Transactional
    public void enviarCodigoReset(String email) {
        String finalEmail = email.trim().toLowerCase(Locale.ROOT);
        CodigoVerificacaoEmail novoCodigo = salvarNovoCodigoReset(finalEmail);
        enviarEmailReset(finalEmail, novoCodigo);
    }

    @Transactional
    public CodigoVerificacaoEmail salvarNovoCodigoReset(String email) {
        LocalDateTime agora = LocalDateTime.now();
        codigoRepository.inativarCodigosAtivosPorEmail(email);

        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        CodigoVerificacaoEmail novoCodigo = CodigoVerificacaoEmail.builder()
                .email(email)
                .codigo(codigo)
                .dataExpiracao(agora.plusMinutes(TEMPO_EXPIRACAO_MINUTOS))
                .tentativas(0)
                .utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.RESET_SENHA)
                .build();

        return codigoRepository.save(novoCodigo);
    }

    private void enviarEmailReset(String email, CodigoVerificacaoEmail novoCodigo) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        String nomeUsuario = (usuario != null && usuario.getNome() != null) ? usuario.getNome() : "Usuário";
        String codigo = novoCodigo.getCodigo();


        String assunto = "Recuperação de Senha - Nhac Delivery";
        
        String htmlConteudo = """
                <!DOCTYPE html>
                <html lang="pt-BR" xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Redefinir senha · Nhac</title>
                <style>
                body, table, td { font-family: 'Roboto', Arial, Helvetica, sans-serif; }
                body { margin:0; padding:0; background-color:#FFE7E5; }
                .email-container { max-width:600px; margin: 0 auto; background-color:#FFE7E5; }
                .header { background-color:#FFE7E5; padding: 25px 30px; }
                .header img { max-height: 50px; display: block; }
                .sub-header { background-color:#FF6961; padding: 15px 30px; font-size:16px; font-weight:700; color:#FFFFFF; }
                .content { padding: 40px 30px; color: #333333; font-size: 15px; line-height: 1.6; }
                .greeting { font-size: 18px; font-weight: 700; color: #FF6961; margin-bottom: 25px; }
                .highlight { background-color: #FCDABB; padding: 2px 4px; border-radius: 4px; font-weight: bold; }
                .code-box { text-align: center; margin: 40px 0; }
                .code { font-size: 44px; font-weight: 700; color: #FF6961; letter-spacing: 4px; }
                </style>
                </head>
                <body style="background-color:#FFE7E5; margin:0; padding:0;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FFE7E5;">
                <tr>
                <td align="center">
                <table role="presentation" class="email-container" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px; max-width:600px; text-align: left;">
                <tr>
                <td class="header" style="background-color:#FFE7E5; padding:25px 30px;">
                  <img src="%s" alt="Nhac" style="max-height:50px; display:block;" />
                </td>
                </tr>
                <tr>
                <td class="sub-header" style="background-color:#FF6961; padding:15px 30px; font-size:16px; font-weight:700; color:#FFFFFF;">
                  Esse é seu código de acesso
                </td>
                </tr>
                <tr>
                <td class="content" style="padding:40px 30px; color:#333333; font-size:15px; line-height:1.6;">
                  <div class="greeting" style="font-size:18px; font-weight:700; color:#FF6961; margin-bottom:25px;">
                    Olá, %s!
                  </div>

                  <div style="margin-bottom:20px;">
                    Para validarmos o acesso ao seu aplicativo com segurança, <strong>use o código abaixo diretamente no seu app do <span class="highlight" style="background-color:#FCDABB; padding:2px 4px; border-radius:4px; font-weight:bold;">Nhac</span></strong>.
                  </div>

                  <div class="code-box" style="text-align:center; margin:40px 0;">
                    <span class="code" style="font-size:44px; font-weight:700; color:#FF6961; letter-spacing:4px;">%s</span>
                  </div>

                  <div style="margin-bottom:20px; text-align: center; color: #8A8A8A; font-size: 14px;">
                    Este código expira em <strong>%d minutos</strong>.
                  </div>
                </td>
                </tr>
                </table>
                </td>
                </tr>
                </table>
                </body>
                </html>
                """.formatted(URL_LOGO_NHAC, nomeUsuario, codigo, TEMPO_EXPIRACAO_MINUTOS);
        emailService.enviarEmailHtml(email, assunto, htmlConteudo);
    }
}
