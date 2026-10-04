package br.com.nhac.backend_nhac.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface FeedComentarioRepository extends JpaRepository<FeedComentario, String> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    Page<FeedComentario> findByPostIdOrderByCriadoEmAscIdAsc(String postId, Pageable pageable);
    Optional<FeedComentario> findByIdAndPostId(String id, String postId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from FeedComentario c where c.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") String postId);
}
