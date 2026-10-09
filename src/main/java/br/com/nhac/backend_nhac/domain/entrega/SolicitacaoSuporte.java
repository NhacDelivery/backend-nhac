package br.com.nhac.backend_nhac.domain.entrega;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="tb_suporte_entrega") @Getter @Setter @NoArgsConstructor
public class SolicitacaoSuporte {
    @Id @Column(length=50) private String id;
    @Column(name="pedido_id", nullable=false, length=50) private String pedidoId;
    @Column(name="usuario_id", nullable=false, length=50) private String usuarioId;
    @Column(nullable=false, length=30) private String motivo;
    @Column(nullable=false, length=2000) private String descricao;
    @Column(nullable=false, length=30) private String etapa;
    @Column(nullable=false, length=30) private String status;
    @Column(length=2000) private String resposta;
    @Column(length=30) private String acao;
    @Column(name="responsavel_usuario_id", length=50) private String responsavelUsuarioId;
    @Column(name="criado_em", nullable=false) private Instant criadoEm;
    @Column(name="respondido_em") private Instant respondidoEm;
}
