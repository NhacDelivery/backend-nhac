package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.usuario.TelefoneNormalizador;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CadastroService {
    private final UsuarioRepository usuarios;
    private final CodigoVerificacaoEmailRepository codigos;
    private final PasswordEncoder encoder;

    public CadastroService(UsuarioRepository usuarios, CodigoVerificacaoEmailRepository codigos, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.encoder = encoder;
    }

    @Transactional
    public Usuario registrar(RegistroRequestDTO dados) {
        String email = dados.email().trim().toLowerCase(Locale.ROOT);
        if (dados.tokenCadastro() == null || dados.tokenCadastro().isBlank())
            throw new RegraDeNegocioException("Confirme seu e-mail antes de concluir o cadastro.");
        var prova = codigos.buscarProvaCadastro(email, VerificacaoEmailService.hashToken(dados.tokenCadastro()),
                LocalDateTime.now().minusMinutes(30))
                .orElseThrow(() -> new RegraDeNegocioException("Confirmação de e-mail inválida, expirada ou já utilizada."));
        if (usuarios.findByEmailIgnoreCase(email).isPresent())
            throw new RegraDeNegocioException("Este e-mail já está em uso.");
        String telefone = TelefoneNormalizador.normalizar(dados.telefone());
        if (telefone != null && usuarios.findByTelefone(telefone).isPresent())
            throw new RegraDeNegocioException("Este telefone já está em uso.");
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setTelefone(telefone);
        usuario.setSenha(encoder.encode(dados.senha()));
        usuario.setEmailVerificado(true);
        usuarios.inserir(usuario);
        prova.setConsumidoEm(LocalDateTime.now());
        prova.setTokenCadastroHash(null);
        return usuario;
    }
}
