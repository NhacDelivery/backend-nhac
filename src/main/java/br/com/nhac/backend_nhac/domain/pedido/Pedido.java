package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "tb_pedidos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pedido_usuario_idempotency",
                columnNames = {"usuario_id", "idempotency_key"}
        )
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Pedido {

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "taxa_frete", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaFrete;

    @Column(name = "forma_pagamento", nullable = false)
    private String formaPagamento;

    @Column(name = "troco_para", precision = 10, scale = 2)
    private BigDecimal trocoPara;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Embedded
    private EnderecoEntrega enderecoEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id")
    private Entregador entregador;

    @Column(name = "entrega_latitude")
    private Double entregaLatitude;

    @Column(name = "entrega_longitude")
    private Double entregaLongitude;

    /**
     * Momento em que o entregador confirmou a retirada na loja (V039).
     * Aceitar a oferta ≠ ter o pedido na mochila: a corrida é atribuída no
     * aceite, mas SAIU_ENTREGA só vale quando ele realmente coleta.
     */
    @Column(name = "coletado_em")
    private Instant coletadoEm;

    /** Momento em que o entregador deu baixa na entrega (V039). */
    @Column(name = "entregue_em")
    private Instant entregueEm;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @Column(name = "pagamento_expira_em")
    private Instant pagamentoExpiraEm;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "asaas_payment_id")
    private String asaasPaymentId;
    
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "idempotency_fingerprint", length = 64)
    private String idempotencyFingerprint;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "cupom_id")
    private String cupomId;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    public void adicionarItem(ItemPedido item) {
        itens.add(item);
        item.setPedido(this);
    }
    
    @Column(name = "codigo_entrega", length = 8)
    private String codigoEntrega;

    @Column(name = "codigo_entrega_tentativas", nullable = false)
    private int codigoEntregaTentativas = 0;

    @Column(name = "codigo_entrega_bloqueado_ate")
    private Instant codigoEntregaBloqueadoAte;

    public void alterarStatus(StatusPedido novoStatus) {
        this.status.podeMudarPara(novoStatus);
        this.status = novoStatus;
        if (novoStatus == StatusPedido.SAIU_ENTREGA && this.codigoEntrega == null) {
            this.codigoEntrega = gerarCodigoEntrega();
        }
    }

    private String gerarCodigoEntrega() {
        int code = new java.security.SecureRandom().nextInt(10000);
        return String.format("%04d", code);
    }
}
