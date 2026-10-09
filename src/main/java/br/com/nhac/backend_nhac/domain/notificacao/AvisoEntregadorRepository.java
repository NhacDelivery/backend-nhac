package br.com.nhac.backend_nhac.domain.notificacao;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface AvisoEntregadorRepository extends JpaRepository<AvisoEntregador,String> {
    Page<AvisoEntregador> findByUsuarioIdOrderByCriadoEmDesc(String usuarioId, Pageable pageable);
    List<AvisoEntregador> findTop20ByPushEnviadoFalseAndTentativasLessThanAndProximaTentativaBeforeOrderByCriadoEmAsc(int limite, Instant agora);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AvisoEntregador a where a.id = :id")
    Optional<AvisoEntregador> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
}
