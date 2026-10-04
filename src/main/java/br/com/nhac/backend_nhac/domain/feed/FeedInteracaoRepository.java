package br.com.nhac.backend_nhac.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FeedInteracaoRepository extends JpaRepository<FeedInteracao, String> {
    Optional<FeedInteracao> findByPostIdAndUsuarioIdAndTipo(String postId, String usuarioId, FeedInteracao.Tipo tipo);
    List<FeedInteracao> findByUsuarioIdAndPostIdIn(String usuarioId, Collection<String> postIds);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from FeedInteracao c where c.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") String postId);
}
