package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface AvaliacaoProdutoRepository extends JpaRepository<AvaliacaoProduto,String> {
    Optional<AvaliacaoProduto> findByPedidoIdAndProdutoId(String pedidoId, String produtoId);
    @EntityGraph(attributePaths={"usuario","produto"})
    @Query("select a from AvaliacaoProduto a where a.produto.id=:id and (:positivas=false or a.nota>=4) and (:fotos=false or size(a.imagens)>0)")
    Page<AvaliacaoProduto> listar(@Param("id") String id, @Param("fotos") boolean fotos, @Param("positivas") boolean positivas, Pageable pageable);
}
