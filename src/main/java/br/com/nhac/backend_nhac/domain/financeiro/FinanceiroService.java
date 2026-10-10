package br.com.nhac.backend_nhac.domain.financeiro;

import br.com.nhac.backend_nhac.domain.financeiro.dto.FinanceiroDTOs.*;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.painel.dto.FaturamentoDiaDTO;
import br.com.nhac.backend_nhac.domain.pedido.ItemPedido;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /lojista/financeiro (item 4.1 da spec). Tudo calculado em cima de
 * tb_pedidos/tb_itens_pedido — nenhuma tabela nova.
 *
 * Decisão assumida (pergunta em aberto #8 da spec, sem resposta ainda):
 * pedidos com status PENDENTE/PAGO/PREPARANDO/SAIU_ENTREGA (ainda não
 * entregues) ENTRAM no faturamento do período — só CANCELADO é excluído.
 * Se a decisão for "só ENTREGUE conta como faturamento", é só trocar o
 * filtro `p.getStatus() != CANCELADO` por `p.getStatus() == ENTREGUE` nos
 * pontos marcados abaixo.
 */
@Service
public class FinanceiroService {

    private static final ZoneId ZONA_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final String[] DIAS_SEMANA_PT = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB"};

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final LojaAccessService lojaAccessService;

    public FinanceiroService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository, LojaAccessService lojaAccessService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.lojaAccessService = lojaAccessService;
    }

    @Transactional(readOnly = true)
    public FinanceiroDTO obterFinanceiro(Usuario usuarioLogado, PeriodoFinanceiro periodo) {
        lojaAccessService.exigirFinanceiro(usuarioLogado);
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        String lojaId = loja.getId();

        LocalDate hoje = LocalDate.now(ZONA_PADRAO);
        LocalDate primeiroDia = switch (periodo) {
            case HOJE -> hoje;
            case SETE_DIAS -> hoje.minusDays(6);
            case TRINTA_DIAS -> hoje.minusDays(29);
        };
        Instant inicio = primeiroDia.atStartOfDay(ZONA_PADRAO).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(ZONA_PADRAO).minusNanos(1).toInstant();

        List<Pedido> todosNoPeriodo = pedidoRepository.findByLojaIdAndPeriodo(lojaId, inicio, fim);
        List<Pedido> validos = todosNoPeriodo.stream().filter(p -> p.getStatus() != StatusPedido.CANCELADO).toList();

        return new FinanceiroDTO(
                periodo.name(),
                calcularResumo(todosNoPeriodo, validos),
                calcularFaturamentoDiario(validos, primeiroDia, hoje),
                calcularPedidosPorDiaSemana(validos),
                calcularVendasPorCategoria(validos),
                calcularVendasPorPagamento(validos),
                calcularProdutosMaisVendidos(lojaId, inicio, fim),
                calcularPedidosPorHora(validos)
        );
    }

    private ResumoFinanceiroDTO calcularResumo(List<Pedido> todos, List<Pedido> validos) {
        BigDecimal faturamento = validos.stream().map(Pedido::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long numeroPedidos = validos.size();
        BigDecimal ticketMedio = numeroPedidos == 0
                ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(numeroPedidos), 2, RoundingMode.HALF_UP);

        long totalComCancelados = todos.size();
        long cancelados = todos.size() - validos.size();
        BigDecimal taxaCancelamento = totalComCancelados == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelados)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalComCancelados), 1, RoundingMode.HALF_UP);

        return new ResumoFinanceiroDTO(faturamento, numeroPedidos, ticketMedio, taxaCancelamento);
    }

    private List<FaturamentoDiaDTO> calcularFaturamentoDiario(List<Pedido> validos, LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, BigDecimal> valorPorDia = validos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCriadoEm().atZone(ZONA_PADRAO).toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Pedido::getValorTotal, BigDecimal::add)
                ));
        return primeiroDia.datesUntil(ultimoDia.plusDays(1))
                .map(dia -> new FaturamentoDiaDTO(dia, valorPorDia.getOrDefault(dia, BigDecimal.ZERO)))
                .toList();
    }

    private List<PedidosPorDiaSemanaDTO> calcularPedidosPorDiaSemana(List<Pedido> validos) {
        Map<DayOfWeek, Long> porDia = validos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCriadoEm().atZone(ZONA_PADRAO).getDayOfWeek(),
                        Collectors.counting()
                ));
        // Segunda a domingo, na ordem que a UI espera
        return java.util.stream.Stream.of(DayOfWeek.values())
                .map(dia -> new PedidosPorDiaSemanaDTO(DIAS_SEMANA_PT[dia.getValue() % 7], porDia.getOrDefault(dia, 0L)))
                .toList();
    }

    private List<VendasPorCategoriaDTO> calcularVendasPorCategoria(List<Pedido> validos) {
        Map<String, BigDecimal> valorPorCategoria = validos.stream()
                .flatMap(p -> p.getItens().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getProduto().getCategoriaMenu(),
                        Collectors.reducing(BigDecimal.ZERO, this::subtotalItem, BigDecimal::add)
                ));
        BigDecimal total = valorPorCategoria.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return valorPorCategoria.entrySet().stream()
                .map(e -> new VendasPorCategoriaDTO(e.getKey(), e.getValue(), calcularPercentual(e.getValue(), total)))
                .sorted(Comparator.comparing(VendasPorCategoriaDTO::valor).reversed())
                .toList();
    }

    private List<VendasPorPagamentoDTO> calcularVendasPorPagamento(List<Pedido> validos) {
        Map<String, BigDecimal> valorPorPagamento = validos.stream()
                .collect(Collectors.groupingBy(
                        Pedido::getFormaPagamento,
                        Collectors.reducing(BigDecimal.ZERO, Pedido::getValorTotal, BigDecimal::add)
                ));
        BigDecimal total = valorPorPagamento.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return valorPorPagamento.entrySet().stream()
                .map(e -> new VendasPorPagamentoDTO(e.getKey(), e.getValue(), calcularPercentual(e.getValue(), total)))
                .sorted(Comparator.comparing(VendasPorPagamentoDTO::valor).reversed())
                .toList();
    }

    private BigDecimal calcularPercentual(BigDecimal valor, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private List<ProdutoMaisVendidoDTO> calcularProdutosMaisVendidos(String lojaId, Instant inicio, Instant fim) {
        List<Object[]> linhas = produtoRepository.rankingProdutosVendidos(lojaId, inicio, fim);
        return linhas.stream()
                .limit(10)
                .map(linha -> new ProdutoMaisVendidoDTO(
                        (String) linha[0],
                        (String) linha[1],
                        ((Number) linha[3]).longValue(),
                        (BigDecimal) linha[4]
                ))
                .toList();
    }

    private List<PedidosPorHoraDTO> calcularPedidosPorHora(List<Pedido> validos) {
        Map<Integer, Long> porHora = validos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCriadoEm().atZone(ZONA_PADRAO).getHour(),
                        Collectors.counting()
                ));
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(hora -> new PedidosPorHoraDTO(hora, porHora.getOrDefault(hora, 0L)))
                .toList();
    }

    private BigDecimal subtotalItem(ItemPedido item) {
        return item.getPrecoHistorico().multiply(BigDecimal.valueOf(item.getQuantidade()));
    }
}
