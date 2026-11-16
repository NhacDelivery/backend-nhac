package br.com.nhac.backend_nhac.domain.loja;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;


@Entity
@DynamicUpdate
@Table(name = "tb_lojas")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Loja {

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @Column(name = "usuario_id", length = 50)
    private String usuarioId;


    private String nome;


    @Column(columnDefinition = "TEXT")
    private String descricao;


    private String categoria;


    @Column(columnDefinition = "TEXT")
    private  String imagemUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "is_aberto", nullable = false)
    private boolean isAberto;

    @Embedded
    private DadosOperacionais dadosOperacionais;

    @Embedded
    private EnderecoLoja endereco;

    @Embedded
    private GeoLocalizacao geoLocalizacao;

    @Embedded
    private HorariosFuncionamento horariosFuncionamento;

    @Embedded
    @Builder.Default
    private FormasPagamento formasPagamento = new FormasPagamento();

}
