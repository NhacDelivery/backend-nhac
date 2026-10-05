package br.com.nhac.backend_nhac.domain.avaliacao;

import br.com.nhac.backend_nhac.domain.avaliacao.Avaliacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, String> {

    boolean existsByPedidoId(String pedidoId);

    Page<Avaliacao> findByLojaId(String lojaId, Pageable pageable);

    long countByLojaIdAndNotaGreaterThanEqual(String lojaId, Integer nota);

    long countByLojaId(String lojaId);

    @Query("SELECT AVG(a.nota) FROM Avaliacao a WHERE a.loja.id = :lojaId")
    Double calcularMediaPorLojaId(String lojaId);
}
