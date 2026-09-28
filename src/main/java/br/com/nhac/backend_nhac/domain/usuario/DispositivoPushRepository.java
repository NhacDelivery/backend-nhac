package br.com.nhac.backend_nhac.domain.usuario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, String> {
    Optional<DispositivoPush> findByTokenHash(String tokenHash);
    List<DispositivoPush> findByUsuarioId(String usuarioId);
    void deleteByUsuarioIdAndTokenHash(String usuarioId, String tokenHash);
}
