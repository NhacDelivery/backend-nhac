package br.com.nhac.backend_nhac.domain.feed;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
@Service
public class FeedTentativaService {
    private final FeedTentativaRepository tentativas;
    private final UsuarioRepository usuarios;
    public FeedTentativaService(FeedTentativaRepository tentativas, UsuarioRepository usuarios) {
        this.tentativas=tentativas; this.usuarios=usuarios;
    }
    @Transactional
    public String executar(Usuario usuario, String escopo, String chave, Object payload, Supplier<String> criar) {
        if (chave == null) return criar.get(); // clientes anteriores continuam compatíveis
        if (!chave.matches("[A-Za-z0-9_-]{1,100}")) throw new RegraDeNegocioException("Idempotency-Key inválida.");
        usuarios.findLockedById(usuario.getId()).orElseThrow(() -> new IdNaoEncontradoException("Usuário não encontrado."));
        String id=hash(usuario.getId()+"|"+escopo+"|"+chave), fingerprint=hash(payload.toString());
        var anterior=tentativas.findById(id);
        if (anterior.isPresent()) {
            if (!anterior.get().getFingerprint().equals(fingerprint))
                throw new IdempotenciaConflitoException("Use o mesmo conteúdo para recuperar esta tentativa.");
            return anterior.get().getRecursoId();
        }
        String recursoId=criar.get();
        var tentativa=new FeedTentativa(); tentativa.setId(id); tentativa.setFingerprint(fingerprint); tentativa.setRecursoId(recursoId);
        tentativas.save(tentativa);
        return recursoId;
    }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
