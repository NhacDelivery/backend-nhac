package br.com.nhac.backend_nhac.domain.feed;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_feed_posts")
@Getter @Setter @NoArgsConstructor
public class FeedPost {
    @Id @Column(length = 50, nullable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id")
    private Loja loja;
    @Column(nullable = false, length = 5000)
    private String conteudo;
    @org.hibernate.annotations.BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "tb_feed_imagens", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "url", nullable = false, length = 2048)
    private List<String> imagens = new ArrayList<>();
    @org.hibernate.annotations.BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "tb_feed_hashtags", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "tag", nullable = false, length = 60)
    private List<String> hashTags = new ArrayList<>();
    @Column(nullable = false)
    private boolean patrocinado;
    @Column(name = "sponsor_label", length = 100)
    private String sponsorLabel;
    @Column(nullable = false)
    private long curtidas;
    @Column(nullable = false)
    private long comentarios;
    @Column(nullable = false)
    private long salvos;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();
}
