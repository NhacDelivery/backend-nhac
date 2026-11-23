package br.com.nhac.backend_nhac.domain.cupom;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CupomRepository extends JpaRepository<Cupom, String> {
    List<Cupom> findByLojaIdOrderByCriadoEmDesc(String lojaId);
    @org.springframework.data.jpa.repository.Query("select count(p) from Pedido p where p.usuarioId = :usuario and p.cupomId = :cupom and p.status <> br.com.nhac.backend_nhac.domain.pedido.StatusPedido.CANCELADO")
    long usosDoCliente(String usuario, String cupom);
    boolean existsByCodigo(String codigo);
    long countByUsuarioId(String usuarioId);
    List<Cupom> findByUsuarioIdOrderByCriadoEmDesc(String usuarioId);
    Optional<Cupom> findByUsuarioIdAndOrigem(String usuarioId, String origem);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cupom> findLockedById(String id);
}
