package br.com.nhac.backend_nhac.domain.favorito;

import br.com.nhac.backend_nhac.domain.favorito.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, String> {
    boolean existsByUsuarioIdAndLojaId(String usuarioId, String lojaId);
    
    Optional<Favorito> findByUsuarioIdAndLojaId(String usuarioId, String lojaId);
    
    long countByLojaId(String lojaId);

    long countByUsuarioId(String usuarioId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "loja")
    org.springframework.data.domain.Page<Favorito> findByUsuarioIdAndLojaIsAbertoTrue(String usuarioId, org.springframework.data.domain.Pageable pageable);
}
