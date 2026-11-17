package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.domain.auth.dto.LoginResponseDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.CodigoSmsRejeitadoException;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsAuthService {

    private final VerificacaoTelefoneService verificacaoTelefoneService;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    @Transactional(noRollbackFor = CodigoSmsRejeitadoException.class)
    public LoginResponseDTO autenticarComSms(ValidarCodigoSmsDTO dto) {
        // Valida o código SMS. Lança exceção se for inválido, expirado ou com limite excedido.
        verificacaoTelefoneService.validarCodigo(dto);

        Optional<Usuario> usuarioExistente = usuarioRepository.findByTelefone(dto.telefone());

        Usuario usuario;
        boolean isNovoUsuario = false;

        if (usuarioExistente.isPresent()) {
            usuario = usuarioExistente.get();
        } else {
            usuario = registrarNovoUsuarioSms(dto.telefone(), dto.nome());
            isNovoUsuario = true;
        }

        String tokenJwt = tokenService.gerarToken(usuario);

        return LoginResponseDTO.from(usuario, tokenJwt, isNovoUsuario);
    }

    private Usuario registrarNovoUsuarioSms(String telefone, String nome) {
        Usuario novoUsuario = new Usuario();
        novoUsuario.setId(UUID.randomUUID().toString());
        // E-mail não é mais gerado para usuários de telefone (Fix B6)
        novoUsuario.setEmail(null); 
        novoUsuario.setNome((nome != null && !nome.trim().isEmpty()) ? nome.trim() : "Novo Usuário");
        novoUsuario.setTelefone(telefone);
        novoUsuario.setTelefoneVerificado(true);
        novoUsuario.setEnderecos(new ArrayList<>());
        novoUsuario.setSenha(null);

        return usuarioRepository.save(novoUsuario);
    }
}
