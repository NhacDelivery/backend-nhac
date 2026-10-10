package br.com.nhac.backend_nhac.domain.entrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.List;
public interface SolicitacaoSuporteRepository extends JpaRepository<SolicitacaoSuporte, String> {
    List<SolicitacaoSuporte> findByPedidoIdAndUsuarioIdOrderByCriadoEmDesc(String pedidoId, String usuarioId);
    Page<SolicitacaoSuporte> findByStatusOrderByCriadoEmAsc(String status, Pageable pageable);
}
