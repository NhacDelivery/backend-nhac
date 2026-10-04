package br.com.nhac.backend_nhac.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FeedInteracaoRepository extends JpaRepository<FeedInteracao, String> {
    Optional<FeedInteracao> findByPostIdAndUsuarioIdAndTipo(String postId, String usuarioId, FeedInteracao.Tipo tipo);
    List<FeedInteracao> findByUsuarioIdAndPostIdIn(String usuarioId, Collection<String> postIds);
    void deleteByPostId(String postId);
}
