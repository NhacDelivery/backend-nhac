package br.com.nhac.backend_nhac.domain.feed;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="tb_feed_denuncias") @Getter @Setter @NoArgsConstructor
public class FeedDenuncia {
    @Id @Column(length=50) private String id;
    @Column(name="usuario_id",length=50,nullable=false) private String usuarioId;
    @Column(name="post_id",length=50,nullable=false) private String postId;
    @Column(name="comentario_id",length=50) private String comentarioId;
    @Column(length=1000,nullable=false) private String motivo;
    @Column(length=30,nullable=false) private String status="ABERTA";
    @Column(name="criado_em",nullable=false) private Instant criadoEm=Instant.now();
}
