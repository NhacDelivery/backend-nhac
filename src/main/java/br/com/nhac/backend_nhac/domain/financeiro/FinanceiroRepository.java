package br.com.nhac.backend_nhac.domain.financeiro;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Agregações sem carregar entidades, itens e produtos do período na JVM. */
public interface FinanceiroRepository extends Repository<Pedido, String> {
    interface PorStatus {
        StatusPedido getStatus();
        Long getQuantidade();
        BigDecimal getTotal();
    }
    interface PorHora {
        Integer getAno(); Integer getMes(); Integer getDia(); Integer getHora();
        String getPagamento(); Long getQuantidade(); BigDecimal getTotal();
    }
    interface PorCategoria {
        String getCategoria(); BigDecimal getTotal();
    }

    @Query("select p.status as status, count(p) as quantidade, sum(p.valorTotal) as total from Pedido p where p.loja.id=:lojaId and p.criadoEm>=:inicio and p.criadoEm<:fim group by p.status")
    List<PorStatus> resumo(@Param("lojaId") String lojaId, @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("select year(p.criadoEm) as ano, month(p.criadoEm) as mes, day(p.criadoEm) as dia, hour(p.criadoEm) as hora, p.formaPagamento as pagamento, count(p) as quantidade, sum(p.valorTotal) as total from Pedido p where p.loja.id=:lojaId and p.criadoEm>=:inicio and p.criadoEm<:fim and p.status<>br.com.nhac.backend_nhac.domain.pedido.StatusPedido.CANCELADO group by year(p.criadoEm),month(p.criadoEm),day(p.criadoEm),hour(p.criadoEm),p.formaPagamento")
    List<PorHora> horas(@Param("lojaId") String lojaId, @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("select i.produto.categoriaMenu as categoria, sum(i.precoHistorico*i.quantidade) as total from ItemPedido i where i.pedido.loja.id=:lojaId and i.pedido.criadoEm>=:inicio and i.pedido.criadoEm<:fim and i.pedido.status<>br.com.nhac.backend_nhac.domain.pedido.StatusPedido.CANCELADO group by i.produto.categoriaMenu")
    List<PorCategoria> categorias(@Param("lojaId") String lojaId, @Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
