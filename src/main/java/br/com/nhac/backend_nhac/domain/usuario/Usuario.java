package br.com.nhac.backend_nhac.domain.usuario;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "tb_usuarios")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Usuario implements UserDetails {

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20, unique = true)
    private String telefone;

    @Column(name = "imagem_url", columnDefinition = "TEXT")
    private String imagemUrl;

    @Column(length = 14, unique = true)
    private String cpf;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EnderecoUsuario> enderecos = new ArrayList<>();

    @Column(length = 255)
    private String senha;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "telefone_verificado", nullable = false)
    @Builder.Default
    private boolean telefoneVerificado = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Papel papel = Papel.CLIENTE;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "email_verificado", nullable = false)
    @Builder.Default
    private boolean emailVerificado = false;

    @Column(name = "loja_vinculada_id", length = 50)
    private String lojaVinculadaId;

    @Column(name = "cargo", length = 30)
    private String cargo;

    @Column(name = "criado_em")
    @Builder.Default
    private java.time.Instant criadoEm = java.time.Instant.now();

    @Column(name = "notificar_novo_pedido", nullable = false)
    @Builder.Default
    private boolean notificarNovoPedido = true;

    @Column(name = "notificar_mensagens", nullable = false)
    @Builder.Default
    private boolean notificarMensagens = true;

    @Column(name = "notificar_avaliacoes", nullable = false)
    @Builder.Default
    private boolean notificarAvaliacoes = false;

    @Column(name = "notificar_novidades", nullable = false)
    @Builder.Default
    private boolean notificarNovidades = false;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.papel.name()));
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.ativo;
    }
}