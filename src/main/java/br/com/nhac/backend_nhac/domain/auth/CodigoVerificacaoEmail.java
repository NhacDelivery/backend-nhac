package br.com.nhac.backend_nhac.domain.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_codigos_verificacao_email")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoVerificacaoEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Builder.Default
    private int tentativas = 0;

    @Builder.Default
    private boolean utilizado = false;

    // Invalidar um OTP não comprova a posse do e-mail. Esta prova é separada.
    @Column(name = "validado_em")
    private LocalDateTime validadoEm;

    @Column(name = "consumido_em")
    private LocalDateTime consumidoEm;

    @Column(name = "token_cadastro_hash", length = 64, unique = true)
    private String tokenCadastroHash;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "tipo", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TipoCodigo tipo = TipoCodigo.RESET_SENHA;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }

    public enum TipoCodigo {
        RESET_SENHA,
        CADASTRO
    }
}
