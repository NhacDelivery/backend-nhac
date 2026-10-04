package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AvaliacaoEntregadorRepository extends JpaRepository<AvaliacaoEntregador, String> {
    boolean existsByPedidoId(String pedidoId);
    Optional<AvaliacaoEntregador> findByPedidoIdAndUsuarioId(String pedidoId, String usuarioId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    Page<AvaliacaoEntregador> findByEntregadorIdOrderByCriadoEmDesc(String entregadorId, Pageable pageable);
    long countByEntregadorId(String entregadorId);

    @Query("SELECT AVG(a.nota) FROM AvaliacaoEntregador a WHERE a.entregador.id = :entregadorId")
    Double calcularMediaPorEntregadorId(@Param("entregadorId") String entregadorId);
    @org.springframework.cache.annotation.Cacheable(cacheNames = "entregadorAvaliacoes", key = "#entregadorId")
    @Query("SELECT new br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.ResumoAvaliacaoDTO(AVG(a.nota), COUNT(a)) FROM AvaliacaoEntregador a WHERE a.entregador.id = :entregadorId")
    br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.ResumoAvaliacaoDTO resumir(@Param("entregadorId") String entregadorId);

    @Override
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "entregadorAvaliacoes", key = "#entity.entregador.id")
    <S extends AvaliacaoEntregador> S save(S entity);
}
