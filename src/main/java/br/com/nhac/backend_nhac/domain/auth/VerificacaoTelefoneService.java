package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.domain.auth.CodigoVerificacao;
import br.com.nhac.backend_nhac.domain.auth.dto.EnviarCodigoSmsDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.exceptions.CodigoSmsRejeitadoException;
import br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoRepository;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificacaoTelefoneService {

    private final CodigoVerificacaoRepository codigoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SmsService smsService;

    private static final int TEMPO_EXPIRACAO_MINUTOS = 5;
    private static final int MAX_TENTATIVAS = 3;

    @Transactional
    public void enviarCodigo(EnviarCodigoSmsDTO dto) {
        String telefone = dto.telefone().trim();

        LocalDateTime agora = LocalDateTime.now();
        codigoRepository.inativarCodigosAtivosPorTelefone(telefone);

        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        CodigoVerificacao novoCodigo = CodigoVerificacao.builder()
                .telefone(telefone)
                .codigo(codigo)
                .dataExpiracao(agora.plusMinutes(TEMPO_EXPIRACAO_MINUTOS))
                .tentativas(0)
                .utilizado(false)
                .build();

        codigoRepository.save(novoCodigo);

        String mensagem = String.format("Nhac Delivery: Seu código de confirmação é %s. Válido por %d minutos.", codigo, TEMPO_EXPIRACAO_MINUTOS);
        smsService.enviarSms(telefone, mensagem);
    }

    @Transactional(noRollbackFor = CodigoSmsRejeitadoException.class)
    public void validarCodigo(ValidarCodigoSmsDTO dto) {
        String telefone = dto.telefone().trim();
        LocalDateTime agora = LocalDateTime.now();

        CodigoVerificacao registro = codigoRepository
                .findTopByTelefoneAndUtilizadoFalseAndDataExpiracaoAfterOrderByCriadoEmDesc(telefone, agora)
                .orElseThrow(() -> new RegraDeNegocioException("Código expirado ou não encontrado. Solicite um novo código."));

        if (registro.getTentativas() >= MAX_TENTATIVAS) {
            registro.setUtilizado(true);
            codigoRepository.save(registro);
            throw new CodigoSmsRejeitadoException("Limite de tentativas excedido para este código. Solicite um novo.");
        }

        if (!registro.getCodigo().equals(dto.codigo().trim())) {
            registro.setTentativas(registro.getTentativas() + 1);
            codigoRepository.save(registro);
            throw new CodigoSmsRejeitadoException("Código de verificação inválido.");
        }

        registro.setUtilizado(true);
        codigoRepository.save(registro);

        usuarioRepository.findByTelefone(telefone).ifPresent(usuario -> {
            usuario.setTelefoneVerificado(true);
            usuarioRepository.save(usuario);
        });
}
}
