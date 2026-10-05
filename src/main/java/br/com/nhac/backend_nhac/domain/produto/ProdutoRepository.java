package br.com.nhac.backend_nhac.domain.produto;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, String> {

    @Query("SELECT p FROM Produto p JOIN FETCH p.loja WHERE p.id = :id AND p.isAtivo = true")
    Optional<Produto> findByIdAndIsAtivoTrue(@Param("id") String id);

    @Query(value = """
        SELECT p FROM Produto p JOIN FETCH p.loja
        WHERE p.isAtivo = true AND p.loja.isAberto = true
          AND p.percentualDesconto > 0 AND p.preco < 20
        """, countQuery = """
        SELECT COUNT(p) FROM Produto p JOIN p.loja
        WHERE p.isAtivo = true AND p.loja.isAberto = true
          AND p.percentualDesconto > 0 AND p.preco < 20
        """)
    Page<Produto> findPromocoes(Pageable pageable);

    long countByLojaId(String lojaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Produto p SET p.estoque = p.estoque - :quantidade WHERE p.id = :id AND p.estoque >= :quantidade")
    int decrementarEstoqueSeDisponivel(@Param("id") String id, @Param("quantidade") int quantidade);

    @Query(value = """
        SELECT p FROM Produto p
        JOIN FETCH p.loja 
        WHERE p.isAtivo = true
        AND (:lojaId IS NULL OR p.loja.id = :lojaId)
        AND (:categoriaMenu IS NULL OR LOWER(p.categoriaMenu) = LOWER(:categoriaMenu))
        AND (:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
        AND (:precoMaximo IS NULL OR p.preco <= :precoMaximo)
    """,
            countQuery = """
        SELECT COUNT(p) FROM Produto p
        JOIN p.loja
        WHERE p.isAtivo = true
        AND (:lojaId IS NULL OR p.loja.id = :lojaId)
        AND (:categoriaMenu IS NULL OR LOWER(p.categoriaMenu) = LOWER(:categoriaMenu))
        AND (:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
        AND (:precoMaximo IS NULL OR p.preco <= :precoMaximo)
    """)
    Page<Produto> findAllWithFilters(
            @Param("lojaId") String lojaId,
            @Param("categoriaMenu") String categoriaMenu,
            @Param("nome") String nome,
            @Param("precoMaximo") BigDecimal precoMaximo,
            Pageable pageable
    );
    @Query(value = """
        SELECT p FROM Produto p
        WHERE p.loja.id = :lojaId
        AND (:categoriaMenu IS NULL OR LOWER(p.categoriaMenu) = LOWER(:categoriaMenu))
        AND (:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
        """,
            countQuery = """
        SELECT COUNT(p) FROM Produto p
        WHERE p.loja.id = :lojaId
        AND (:categoriaMenu IS NULL OR LOWER(p.categoriaMenu) = LOWER(:categoriaMenu))
        AND (:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :nome, '%')))
        """)
    Page<Produto> findByLoja(
            @Param("lojaId") String lojaId,
            @Param("categoriaMenu") String categoriaMenu,
            @Param("nome") String nome,
            Pageable pageable
    );

    @Query("SELECT p FROM Produto p JOIN FETCH p.loja WHERE p.id = :id AND p.loja.id = :lojaId")
    Optional<Produto> findByIdAndLojaId(@Param("id") String id, @Param("lojaId") String lojaId);

    @Query("""
        SELECT i.produto.id as produtoId, i.produto.nome as nome, i.produto.categoriaMenu as categoria,
               SUM(i.quantidade) as quantidadeVendida, SUM(i.precoHistorico * i.quantidade) as faturamento
        FROM ItemPedido i
        WHERE i.pedido.loja.id = :lojaId
        AND i.pedido.criadoEm >= :inicio AND i.pedido.criadoEm <= :fim
        AND i.pedido.status <> br.com.nhac.backend_nhac.domain.pedido.StatusPedido.CANCELADO
        GROUP BY i.produto.id, i.produto.nome, i.produto.categoriaMenu
        ORDER BY SUM(i.quantidade) DESC
        """)
    java.util.List<Object[]> rankingProdutosVendidos(
            @Param("lojaId") String lojaId,
            @Param("inicio") java.time.Instant inicio,
            @Param("fim") java.time.Instant fim
    );

    Page<Produto> findByLojaIdAndIsAtivoTrue(String lojaId, Pageable pageable);

    Page<Produto> findByPrecoLessThanEqualAndIsAtivoTrue(BigDecimal precoMaximo, Pageable pageable);

    Page<Produto> findByCategoriaMenuIgnoreCaseAndIsAtivoTrue(String categoriaMenu, Pageable pageable);

    Page<Produto> findByNomeContainingIgnoreCaseAndIsAtivoTrue(String nome, Pageable pageable);

    Page<Produto> findByIsAtivoTrue(Pageable pageable);

    @Query("SELECT new br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO(COUNT(a.id), AVG(a.nota)) " +
           "FROM AvaliacaoProduto a " +
           "WHERE a.produto.id = :produtoId")
    br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO getResumoAvaliacoesPorProdutoId(@Param("produtoId") String produtoId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="loja")
    @Query(value="select p from Produto p where p.loja.id=:lojaId and p.isAtivo=true order by (select coalesce(sum(i.quantidade),0) from ItemPedido i where i.produto=p and i.pedido.status=br.com.nhac.backend_nhac.domain.pedido.StatusPedido.ENTREGUE) desc, p.id asc",
        countQuery="select count(p) from Produto p where p.loja.id=:lojaId and p.isAtivo=true")
    Page<Produto> catalogoVendidos(@Param("lojaId") String lojaId, Pageable pageable);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="loja")
    @Query("select p from Produto p where p.loja.id=:lojaId and p.isAtivo=true and p.percentualDesconto>0 order by p.percentualDesconto desc, p.id asc")
    Page<Produto> catalogoDestaques(@Param("lojaId") String lojaId, Pageable pageable);
}