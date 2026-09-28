package br.com.nhac.backend_nhac.domain.produto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoCreateDTO;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Produto {

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "categoria_menu", nullable = false, length = 50)
    private String categoriaMenu;

    @Column(name = "imagem_url", columnDefinition = "TEXT")
    private String imagemUrl;

    @Column(name = "is_ativo", nullable = false)
    private boolean isAtivo = true;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @Column(length = 20)
    private String peso;

    @Column(name = "percentual_desconto")
    private Integer percentualDesconto;
    
    @Column(name = "estoque")
    private Integer estoque = 100;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 25)
    private List<GrupoAdicional> adicionais = new ArrayList<>();

    public Produto(ProdutoCreateDTO dto, Loja loja) {
        this.loja = loja;
        this.nome = dto.nome();
        this.descricao = dto.descricao();
        this.preco = dto.preco();
        this.categoriaMenu = dto.categoriaMenu();
        this.imagemUrl = dto.imagemUrl();
        this.peso = dto.peso();
        this.percentualDesconto = dto.percentualDesconto();
        this.isAtivo = true;
        this.criadoEm = Instant.now();
        substituirAdicionais(dto.adicionais());
    }

    public void substituirAdicionais(List<br.com.nhac.backend_nhac.domain.produto.dto.GrupoAdicionalDTO> grupos) {
        if (grupos == null) return;
        adicionais.clear();
        for (var dadosGrupo : grupos) {
            GrupoAdicional grupo = new GrupoAdicional();
            grupo.setProduto(this);
            grupo.setNome(dadosGrupo.nome());
            grupo.setObrigatorio(dadosGrupo.obrigatorio());
            grupo.setMinimo(dadosGrupo.minimo());
            grupo.setMaximo(dadosGrupo.maximo());
            for (var dadosItem : dadosGrupo.itens()) {
                ItemAdicional item = new ItemAdicional();
                item.setGrupoAdicional(grupo);
                item.setNome(dadosItem.nome());
                item.setPreco(dadosItem.preco());
                grupo.getItens().add(item);
            }
            adicionais.add(grupo);
        }
    }
}
