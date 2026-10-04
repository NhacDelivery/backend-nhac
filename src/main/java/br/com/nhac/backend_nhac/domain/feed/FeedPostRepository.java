package br.com.nhac.backend_nhac.domain.feed;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface FeedPostRepository extends JpaRepository<FeedPost, String> {
    @EntityGraph(attributePaths = {"usuario", "loja"})
    @Query("select p from FeedPost p where p.id = :id")
    Optional<FeedPost> buscarComRelacionamentos(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FeedPost p where p.id = :id")
    Optional<FeedPost> buscarComBloqueio(@Param("id") String id);

    @EntityGraph(attributePaths = {"usuario", "loja"})
    @Query("select p from FeedPost p where p.usuario.ativo = true and (:promocoes = false or p.patrocinado = true)")
    Page<FeedPost> listar(@Param("promocoes") boolean promocoes, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "loja"})
    @Query("select p from FeedPost p where p.usuario.ativo = true and exists "
            + "(select i.id from FeedInteracao i where i.post = p and i.usuario.id = :usuarioId and i.tipo = :tipo)")
    Page<FeedPost> listarSalvos(@Param("usuarioId") String usuarioId,
            @Param("tipo") FeedInteracao.Tipo tipo, Pageable pageable);
}
