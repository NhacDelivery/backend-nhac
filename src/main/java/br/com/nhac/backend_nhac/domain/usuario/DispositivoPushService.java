package br.com.nhac.backend_nhac.domain.usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispositivoPushService {
    private final DispositivoPushRepository repository;

    public DispositivoPushService(DispositivoPushRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String usuarioId, String token) {
        String normalizado = token.trim();
        String hash = hash(normalizado);
        DispositivoPush dispositivo = repository.findByTokenHash(hash)
                .orElseGet(() -> new DispositivoPush(UUID.randomUUID().toString(), usuarioId, hash, normalizado));
        dispositivo.setUsuarioId(usuarioId);
        repository.save(dispositivo);
    }

    @Transactional
    public void remover(String usuarioId, String token) {
        repository.deleteByUsuarioIdAndTokenHash(usuarioId, hash(token.trim()));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
