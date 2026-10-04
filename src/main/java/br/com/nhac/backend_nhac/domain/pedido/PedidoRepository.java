package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PedidoRepository extends JpaRepository<Pedido, String> {
    @Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("UPDATE Pedido p SET p.pagamentoCriacaoIncerta = :incerta, p.version = p.version + 1 WHERE p.id = :id")
    int marcarCriacaoPagamento(@Param("id") String id, @Param("incerta") boolean incerta);
    @Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("UPDATE Pedido p SET p.asaasPaymentId = :paymentId, p.pagamentoCriacaoIncerta = false, p.version = p.version + 1 WHERE p.id = :id AND (p.asaasPaymentId IS NULL OR p.asaasPaymentId = :paymentId)")
    int vincularAsaas(@Param("id") String id, @Param("paymentId") String paymentId);
    @Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("UPDATE Pedido p SET p.stripePaymentIntentId = :paymentId, p.pagamentoCriacaoIncerta = false, p.version = p.version + 1 WHERE p.id = :id AND (p.stripePaymentIntentId IS NULL OR p.stripePaymentIntentId = :paymentId)")
    int vincularStripe(@Param("id") String id, @Param("paymentId") String paymentId);

    Optional<Pedido> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<Pedido> findByAsaasPaymentId(String asaasPaymentId);
    Optional<Pedido> findByUsuarioIdAndIdempotencyKey(String usuarioId, String idempotencyKey);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"loja", "entregador"})
    Optional<Pedido> findFirstByUsuarioIdAndStatusInOrderByCriadoEmDesc(String usuarioId, List<StatusPedido> status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"loja", "entregador"})
    List<Pedido> findByUsuarioIdAndStatusInOrderByCriadoEmDesc(String usuarioId, List<StatusPedido> status);

    List<Pedido> findByStatusAndPagamentoExpiraEmLessThanEqual(StatusPedido status, Instant agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findLockedById(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Pedido p SET p.entregador = :entregador, p.version = p.version + 1 WHERE p.id = :pedidoId AND p.entregador IS NULL AND p.status = br.com.nhac.backend_nhac.domain.pedido.StatusPedido.PREPARANDO")
    int atribuirEntregadorSeDisponivel(
            @Param("pedidoId") String pedidoId,
            @Param("entregador") Entregador entregador
    );

    @Modifying
    @Query("UPDATE Pedido p SET p.codigoEntregaTentativas = p.codigoEntregaTentativas + 1, p.codigoEntregaBloqueadoAte = :bloqueadoAte WHERE p.id = :pedidoId")
    int incrementarTentativasCodigoEntrega(@Param("pedidoId") String pedidoId, @Param("bloqueadoAte") Instant bloqueadoAte);

    @Modifying
    @Query("UPDATE Pedido p SET p.codigoEntregaTentativas = 0, p.codigoEntregaBloqueadoAte = null WHERE p.id = :pedidoId")
    int resetarTentativasCodigoEntrega(@Param("pedidoId") String pedidoId);

    boolean existsByEntregadorIdAndStatusIn(String entregadorId, java.util.List<StatusPedido> status);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"loja", "entregador"})
    Page<Pedido> findByUsuarioId(String usuarioId, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT p FROM Pedido p
        JOIN FETCH p.loja
        WHERE p.loja.id = :lojaId
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.criadoEm DESC
        """,
            countQuery = """
        SELECT COUNT(p) FROM Pedido p
        WHERE p.loja.id = :lojaId
        AND (:status IS NULL OR p.status = :status)
        """)
    Page<Pedido> findByLoja(
            @Param("lojaId") String lojaId,
            @Param("status") StatusPedido status,
            Pageable pageable
    );

    long countByUsuarioId(String usuarioId);
    long countByUsuarioIdAndCupomIdIsNotNull(String usuarioId);

    long countByLojaIdAndStatusIn(String lojaId, java.util.List<StatusPedido> status);

    java.util.List<Pedido> findTop5ByLojaIdOrderByCriadoEmDesc(String lojaId);

    @Query("SELECT p FROM Pedido p WHERE p.loja.id = :lojaId AND p.criadoEm >= :inicio AND p.criadoEm <= :fim")
    java.util.List<Pedido> findByLojaIdAndPeriodo(
            @Param("lojaId") String lojaId,
            @Param("inicio") java.time.Instant inicio,
            @Param("fim") java.time.Instant fim
    );

    Optional<Pedido> findFirstByEntregadorIdAndStatusIn(String entregadorId, java.util.List<StatusPedido> status);

    // ---------- Entregador (V039) ----------

    /**
     * Histórico de corridas do entregador. COALESCE(entregueEm, criadoEm)
     * porque os pedidos criados antes da V039 não têm entregue_em preenchido —
     * sem o fallback eles sumiriam da ordenação.
     */
    @Query(value = """
        SELECT p FROM Pedido p
        JOIN FETCH p.loja
        WHERE p.entregador.id = :entregadorId
          AND (:status IS NULL OR p.status = :status)
        ORDER BY COALESCE(p.entregueEm, p.criadoEm) DESC
        """,
            countQuery = """
        SELECT COUNT(p) FROM Pedido p
        WHERE p.entregador.id = :entregadorId
          AND (:status IS NULL OR p.status = :status)
        """)
    Page<Pedido> findHistoricoDoEntregador(
            @Param("entregadorId") String entregadorId,
            @Param("status") StatusPedido status,
            Pageable pageable
    );

    @Query("""
        SELECT new br.com.nhac.backend_nhac.domain.entregador.dto.FreteHoraDTO(
          YEAR(COALESCE(p.entregueEm, p.criadoEm)), MONTH(COALESCE(p.entregueEm, p.criadoEm)), DAY(COALESCE(p.entregueEm, p.criadoEm)), HOUR(COALESCE(p.entregueEm, p.criadoEm)), SUM(COALESCE(p.taxaFrete, 0)), COUNT(p))
        FROM Pedido p
        WHERE p.entregador.id = :entregadorId AND p.status = :status
          AND COALESCE(p.entregueEm, p.criadoEm) >= :inicio AND COALESCE(p.entregueEm, p.criadoEm) < :fim
        GROUP BY YEAR(COALESCE(p.entregueEm, p.criadoEm)), MONTH(COALESCE(p.entregueEm, p.criadoEm)), DAY(COALESCE(p.entregueEm, p.criadoEm)), HOUR(COALESCE(p.entregueEm, p.criadoEm))
        """)
    List<br.com.nhac.backend_nhac.domain.entregador.dto.FreteHoraDTO> somarFretesPorHora(
            @Param("entregadorId") String entregadorId, @Param("status") StatusPedido status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    long countByEntregadorIdAndStatus(String entregadorId, StatusPedido status);
}
