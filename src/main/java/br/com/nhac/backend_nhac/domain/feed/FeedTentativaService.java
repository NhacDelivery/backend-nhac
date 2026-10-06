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
        String id=hash(usuario.getId()+"|"+escopo+"|"+chave), fingerprint=hash(canonical(payload));
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
    private String canonical(Object payload) {
        var b = new StringBuilder();
        if (payload instanceof FeedPostCreateDTO dto) {
            append(b, dto.conteudo()); append(b, dto.imagens()); append(b, dto.hashTags());
            append(b, dto.lojaId()); append(b, dto.isPatrocinado()); append(b, dto.sponsorLabel());
        } else if (payload instanceof FeedComentarioCreateDTO dto) { append(b, dto.conteudo()); if (dto.respostaAId() != null) append(b, dto.respostaAId()); }
        else throw new IllegalArgumentException("Payload não suportado.");
        return b.toString();
    }
    private void append(StringBuilder b, Object valor) {
        if (valor == null) { b.append("N;"); return; }
        if (valor instanceof java.util.List<?> lista) {
            b.append("L").append(lista.size()).append(":");
            for (Object item : lista) append(b, item);
        } else { String s = valor.toString(); b.append("S").append(s.length()).append(":").append(s); }
    }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
