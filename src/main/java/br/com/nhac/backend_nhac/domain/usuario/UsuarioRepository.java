package br.com.nhac.backend_nhac.domain.usuario;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByTelefone(String telefone);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Usuario u set u.fcmToken=null where u.fcmToken=:token and u.id<>:id")
    void removerTokenDeOutrasContas(@org.springframework.data.repository.query.Param("token") String token,
            @org.springframework.data.repository.query.Param("id") String id);

    boolean existsByCpfAndIdNot(String cpf, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findLockedById(String id);

    // Funcionários de uma loja (Papel.FUNCIONARIO vinculado a lojaVinculadaId)
    Page<Usuario> findByLojaVinculadaIdOrderByCriadoEmDesc(String lojaVinculadaId, Pageable pageable);

}
