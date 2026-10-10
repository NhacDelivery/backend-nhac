package br.com.nhac.backend_nhac.domain.entrega;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoSuporteRepository extends JpaRepository<SolicitacaoSuporte, String> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from SolicitacaoSuporte s where s.id = :id")
  java.util.Optional<SolicitacaoSuporte> findLockedById(
      @org.springframework.data.repository.query.Param("id") String id);

  List<SolicitacaoSuporte> findByPedidoIdAndUsuarioIdOrderByCriadoEmDesc(
      String pedidoId, String usuarioId);

  Page<SolicitacaoSuporte> findByStatusOrderByCriadoEmAsc(String status, Pageable pageable);
}
