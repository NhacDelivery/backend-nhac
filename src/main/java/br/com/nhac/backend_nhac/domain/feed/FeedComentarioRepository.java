package br.com.nhac.backend_nhac.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface FeedComentarioRepository extends JpaRepository<FeedComentario, String> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    Page<FeedComentario> findByPostIdOrderByCriadoEmAscIdAsc(String postId, Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    @org.springframework.data.jpa.repository.Query("select c from FeedComentario c where c.post.id = :postId and (:autorId is null or c.usuario.id = :autorId)")
    Page<FeedComentario> listar(@org.springframework.data.repository.query.Param("postId") String postId,
            @org.springframework.data.repository.query.Param("autorId") String autorId, Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    @org.springframework.data.jpa.repository.Query("select c from FeedComentario c where c.post.id in :ids and not exists (select c2.id from FeedComentario c2 where c2.post = c.post and (c2.criadoEm < c.criadoEm or (c2.criadoEm = c.criadoEm and c2.id < c.id)))")
    java.util.List<FeedComentario> destacados(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);
    Optional<FeedComentario> findByIdAndPostId(String id, String postId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from FeedComentario c where c.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") String postId);
}
