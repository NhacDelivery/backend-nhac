package br.com.nhac.backend_nhac.domain.notificacao;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="tb_avisos_entregador") @Getter @Setter @NoArgsConstructor
public class AvisoEntregador {
    @Id @Column(length=100) private String id;
    @Column(name="usuario_id",nullable=false,length=50) private String usuarioId;
    @Column(nullable=false,length=30) private String tipo;
    @Column(nullable=false,length=200) private String texto;
    @Column(name="pedido_id",length=50) private String pedidoId;
    @Column(name="loja_id",length=50) private String lojaId;
    @Column(name="loja_nome",length=100) private String lojaNome;
    @Column(name="oferta_id",length=50) private String ofertaId;
    @Column(name="criado_em",nullable=false) private Instant criadoEm;
    @Column(name="push_enviado",nullable=false) private boolean pushEnviado;
    @Column(nullable=false) private int tentativas;
    @Column(name="proxima_tentativa",nullable=false) private Instant proximaTentativa;
}
