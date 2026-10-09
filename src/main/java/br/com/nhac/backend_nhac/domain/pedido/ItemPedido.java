package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.produto.Produto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tb_itens_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ItemPedido {

    public ItemPedido(String id, Pedido pedido, Produto produto, String nome, String imagemUrl, BigDecimal precoHistorico, Integer quantidade) {
        this.id=id; this.pedido=pedido; this.produto=produto; this.nome=nome; this.imagemUrl=imagemUrl; this.precoHistorico=precoHistorico; this.quantidade=quantidade;
    }

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "nome_historico", nullable = false)
    private String nome;

    @Column(name = "imagem_url_historica", columnDefinition = "TEXT")
    private String imagemUrl;

    @Column(name = "preco_historico", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoHistorico;

    @Column(nullable = false)
    private Integer quantidade;
    @ElementCollection
    @CollectionTable(name = "tb_item_pedido_adicionais", joinColumns = @JoinColumn(name = "item_pedido_id"))
    @Column(name = "descricao", nullable = false, length = 200)
    private java.util.List<String> adicionais = new java.util.ArrayList<>();
}