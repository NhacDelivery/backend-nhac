package br.com.nhac.backend_nhac.domain.cupom;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CupomRepository extends JpaRepository<Cupom, String> {
    long countByUsuarioId(String usuarioId);
    List<Cupom> findByUsuarioIdOrderByCriadoEmDesc(String usuarioId);
    Optional<Cupom> findByUsuarioIdAndOrigem(String usuarioId, String origem);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cupom> findLockedById(String id);
}
