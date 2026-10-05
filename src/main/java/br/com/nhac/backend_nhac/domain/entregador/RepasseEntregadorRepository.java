package br.com.nhac.backend_nhac.domain.entregador;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface RepasseEntregadorRepository extends JpaRepository<RepasseEntregador,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RepasseEntregador r where r.pedidoId=:id")
    Optional<RepasseEntregador> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
    boolean existsByReferenciaPagamento(String referenciaPagamento);
}
