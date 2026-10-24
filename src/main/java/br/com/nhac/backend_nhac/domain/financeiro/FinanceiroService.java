package br.com.nhac.backend_nhac.domain.financeiro;

import br.com.nhac.backend_nhac.domain.financeiro.dto.FinanceiroDTOs.*;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.painel.dto.FaturamentoDiaDTO;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mantém a regra existente: todos os pedidos não cancelados compõem o período. */
@Service
public class FinanceiroService {
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final String[] DIAS = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB"};
    private final FinanceiroRepository financeiro;
    private final ProdutoRepository produtos;
    private final LojaAccessService acesso;

    public FinanceiroService(FinanceiroRepository financeiro, ProdutoRepository produtos, LojaAccessService acesso) {
        this.financeiro = financeiro; this.produtos = produtos; this.acesso = acesso;
    }

    @Transactional(readOnly = true)
    public FinanceiroDTO obterFinanceiro(Usuario usuario, PeriodoFinanceiro periodo) {
        acesso.exigirFinanceiro(usuario);
        String lojaId = acesso.obterLojaAcessivel(usuario).getId();
        LocalDate hoje = LocalDate.now(ZONA);
        LocalDate primeiro = switch (periodo) {
            case HOJE -> hoje;
            case SETE_DIAS -> hoje.minusDays(6);
            case TRINTA_DIAS -> hoje.minusDays(29);
        };
        Instant inicio = primeiro.atStartOfDay(ZONA).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(ZONA).toInstant();
        var resumo = financeiro.resumo(lojaId, inicio, fim);
        long todos = resumo.stream().mapToLong(FinanceiroRepository.PorStatus::getQuantidade).sum();
        long cancelados = resumo.stream().filter(r -> r.getStatus() == StatusPedido.CANCELADO)
                .mapToLong(FinanceiroRepository.PorStatus::getQuantidade).sum();
        long validos = todos - cancelados;
        BigDecimal total = resumo.stream().filter(r -> r.getStatus() != StatusPedido.CANCELADO)
                .map(FinanceiroRepository.PorStatus::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        Map<DayOfWeek, Long> porSemana = new EnumMap<>(DayOfWeek.class);
        Map<Integer, Long> porHora = new HashMap<>();
        Map<String, BigDecimal> pagamentos = new TreeMap<>();
        for (var hora : financeiro.horas(lojaId, inicio, fim)) {
            // O armazenamento e a sessão JDBC usam UTC; a apresentação usa São Paulo.
            var local = LocalDateTime.of(hora.getAno(), hora.getMes(), hora.getDia(), hora.getHora(), 0)
                    .toInstant(ZoneOffset.UTC).atZone(ZONA);
            porDia.merge(local.toLocalDate(), hora.getTotal(), BigDecimal::add);
            porSemana.merge(local.getDayOfWeek(), hora.getQuantidade(), Long::sum);
            porHora.merge(local.getHour(), hora.getQuantidade(), Long::sum);
            pagamentos.merge(hora.getPagamento(), hora.getTotal(), BigDecimal::add);
        }
        var categorias = financeiro.categorias(lojaId, inicio, fim);
        BigDecimal totalItens = categorias.stream().map(FinanceiroRepository.PorCategoria::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var ranking = produtos.rankingProdutosVendidos(lojaId, inicio, fim, PageRequest.of(0, 10)).stream()
                .map(r -> new ProdutoMaisVendidoDTO((String) r[0], (String) r[1],
                        ((Number) r[3]).longValue(), (BigDecimal) r[4])).toList();
        return new FinanceiroDTO(periodo.name(),
                new ResumoFinanceiroDTO(total, validos, dividir(total, validos),
                        percentual(BigDecimal.valueOf(cancelados), BigDecimal.valueOf(todos))),
                primeiro.datesUntil(hoje.plusDays(1))
                        .map(d -> new FaturamentoDiaDTO(d, porDia.getOrDefault(d, BigDecimal.ZERO))).toList(),
                Arrays.stream(DayOfWeek.values())
                        .map(d -> new PedidosPorDiaSemanaDTO(DIAS[d.getValue() % 7], porSemana.getOrDefault(d, 0L))).toList(),
                categorias.stream().map(c -> new VendasPorCategoriaDTO(c.getCategoria(), c.getTotal(), percentual(c.getTotal(), totalItens)))
                        .sorted(Comparator.comparing(VendasPorCategoriaDTO::valor).reversed()).toList(),
                pagamentos.entrySet().stream().map(e -> new VendasPorPagamentoDTO(e.getKey(), e.getValue(), percentual(e.getValue(), total)))
                        .sorted(Comparator.comparing(VendasPorPagamentoDTO::valor).reversed()).toList(),
                ranking,
                java.util.stream.IntStream.range(0, 24)
                        .mapToObj(h -> new PedidosPorHoraDTO(h, porHora.getOrDefault(h, 0L))).toList());
    }

    private BigDecimal dividir(BigDecimal valor, long quantidade) {
        return quantidade == 0 ? BigDecimal.ZERO : valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentual(BigDecimal valor, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : valor.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
