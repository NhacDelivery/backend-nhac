package br.com.nhac.backend_nhac.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface FeedComentarioRepository extends JpaRepository<FeedComentario, String> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "usuario")
    Page<FeedComentario> findByPostIdOrderByCriadoEmAscIdAsc(String postId, Pageable pageable);
    Optional<FeedComentario> findByIdAndPostId(String id, String postId);
    void deleteByPostId(String postId);
}
