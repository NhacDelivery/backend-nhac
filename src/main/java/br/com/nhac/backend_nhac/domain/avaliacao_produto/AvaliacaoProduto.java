package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="tb_avaliacoes_produto", uniqueConstraints=@UniqueConstraint(columnNames={"pedido_id","produto_id"}))
@Getter @Setter @NoArgsConstructor
public class AvaliacaoProduto {
    @Id @Column(length=50) private String id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_id", nullable=false) private Pedido pedido;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="produto_id", nullable=false) private Produto produto;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="usuario_id", nullable=false) private Usuario usuario;
    @Column(nullable=false) private Integer nota;
    @Column(length=500) private String comentario;
    @Column(name="criado_em", nullable=false) private Instant criadoEm=Instant.now();
    @ElementCollection @org.hibernate.annotations.BatchSize(size=50)
    @CollectionTable(name="tb_avaliacao_produto_fotos", joinColumns=@JoinColumn(name="avaliacao_id"))
    @OrderColumn(name="ordem") @Column(name="url", length=2048) private List<String> imagens=new ArrayList<>();
}
