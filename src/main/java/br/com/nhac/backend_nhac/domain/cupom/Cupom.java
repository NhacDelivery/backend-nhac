package br.com.nhac.backend_nhac.domain.cupom;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_cupons", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "origem"}))
@Getter @Setter
public class Cupom {
    @Id @Column(length = 50) private String id;
    @Column(nullable = false, unique = true, length = 50) private String codigo;
    private String descricao;
    @Column(name = "tipo_desconto", nullable = false) private String tipoDesconto;
    @Column(name = "valor_desconto", nullable = false, precision = 10, scale = 2) private BigDecimal valorDesconto;
    @Column(name = "valor_minimo_pedido", precision = 10, scale = 2) private BigDecimal valorMinimoPedido;
    @Column(name = "data_validade") private LocalDateTime dataValidade;
    @Column(nullable = false) private boolean ativo = true;
    @Column(name = "limite_usos") private Integer limiteUsos = 1;
    @Column(name = "usos_atuais") private Integer usosAtuais = 0;
    @Column(name = "criado_em") private LocalDateTime criadoEm;
    @Column(name = "usuario_id", length = 50) private String usuarioId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "loja_id") private br.com.nhac.backend_nhac.domain.loja.Loja loja;
    @Column(name = "inicio") private LocalDateTime inicio;
    @Column(length = 30) private String origem;
}
