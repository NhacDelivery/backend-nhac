package br.com.nhac.backend_nhac.domain.feed;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="tb_feed_tentativas") @Getter @Setter @NoArgsConstructor
public class FeedTentativa {
    @Id @Column(length=64) private String id;
    @Column(nullable=false, length=64) private String fingerprint;
    @Column(name="recurso_id", nullable=false, length=50) private String recursoId;
}
