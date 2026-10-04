package br.com.nhac.backend_nhac.domain.feed;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tb_feed_comentarios")
@Getter @Setter @NoArgsConstructor
public class FeedComentario {
    @Id @Column(length = 50, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 2000)
    private String conteudo;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();
}
