package br.com.nhac.backend_nhac.domain.entregador;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="tb_repasses_entregador") @Getter @Setter @NoArgsConstructor
public class RepasseEntregador {
    @Id @Column(name="pedido_id",length=50) private String pedidoId;
    @Column(name="entregador_id",nullable=false,length=50) private String entregadorId;
    @Column(name="valor_devido",nullable=false,precision=12,scale=2) private BigDecimal valorDevido;
    @Column(name="valor_pago",nullable=false,precision=12,scale=2) private BigDecimal valorPago=BigDecimal.ZERO;
    @Column(name="apurado_em",nullable=false) private Instant apuradoEm;
    @Column(name="pago_em") private Instant pagoEm;
    @Column(name="referencia_pagamento",length=100) private String referenciaPagamento;
    @Version private Long version;
}
