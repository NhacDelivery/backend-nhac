package br.com.nhac.backend_nhac.domain.feed;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_feed_interacoes", uniqueConstraints = @UniqueConstraint(
        name = "uk_feed_interacao", columnNames = {"post_id", "usuario_id", "tipo"}))
@Getter @Setter @NoArgsConstructor
public class FeedInteracao {
    public enum Tipo { CURTIDA, SALVO }
    @Id @Column(length = 50, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Tipo tipo;
}
