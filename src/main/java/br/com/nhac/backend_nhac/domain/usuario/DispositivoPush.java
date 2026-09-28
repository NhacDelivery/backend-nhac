package br.com.nhac.backend_nhac.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tb_dispositivos_push")
public class DispositivoPush {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "usuario_id", nullable = false, length = 50)
    private String usuarioId;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Column(nullable = false, length = 2048)
    private String token;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected DispositivoPush() {}

    public DispositivoPush(String id, String usuarioId, String tokenHash, String token) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.token = token;
        this.criadoEm = Instant.now();
    }

    public String getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public String getTokenHash() { return tokenHash; }
    public String getToken() { return token; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
}
