package br.com.nhac.backend_nhac.domain.entrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.List;
public interface SolicitacaoSuporteRepository extends JpaRepository<SolicitacaoSuporte, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SolicitacaoSuporte s where s.id = :id")
    java.util.Optional<SolicitacaoSuporte> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
    List<SolicitacaoSuporte> findByPedidoIdAndUsuarioIdOrderByCriadoEmDesc(String pedidoId, String usuarioId);
    Page<SolicitacaoSuporte> findByStatusOrderByCriadoEmAsc(String status, Pageable pageable);
}
