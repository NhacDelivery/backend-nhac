package br.com.nhac.backend_nhac.domain.painel;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.painel.dto.FaturamentoDiaDTO;
import br.com.nhac.backend_nhac.domain.painel.dto.PainelResumoDTO;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoResumoLojistaMapper;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /lojista/painel — resumo enxuto pra tela inicial do painel (item 3.1 da spec).
 *
 * Decisão assumida (pergunta em aberto #6 da spec, sem resposta ainda): "hoje"
 * é calculado no fuso America/Sao_Paulo, não no fuso do servidor nem do
 * usuário — mais simples de implementar agora e correto pra a esmagadora
 * maioria dos lojistas. Se isso precisar variar por loja/usuário no futuro,
 * é só trocar ZONA_PADRAO por um valor vindo da Loja ou do Usuario.
 *
 * Consulta sem cache: totais financeiros e pedidos precisam refletir as
 * alterações atuais. O cache público de catálogo não participa desta consulta.
 */
@Service
public class PainelService {

    private static final ZoneId ZONA_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final List<StatusPedido> STATUS_EM_PREPARO = List.of(StatusPedido.PENDENTE, StatusPedido.PAGO, StatusPedido.PREPARANDO);
    private static final List<StatusPedido> STATUS_A_CAMINHO = List.of(StatusPedido.SAIU_ENTREGA);

    private final PedidoRepository pedidoRepository;
    private final PedidoResumoLojistaMapper pedidoResumoLojistaMapper;
    private final LojaAccessService lojaAccessService;

    public PainelService(PedidoRepository pedidoRepository, PedidoResumoLojistaMapper pedidoResumoLojistaMapper, LojaAccessService lojaAccessService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoResumoLojistaMapper = pedidoResumoLojistaMapper;
        this.lojaAccessService = lojaAccessService;
    }

    @Transactional(readOnly = true)
    public PainelResumoDTO obterResumo(Usuario usuarioLogado) {
        lojaAccessService.exigirCatalogo(usuarioLogado);
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        String lojaId = loja.getId();

        LocalDate hoje = LocalDate.now(ZONA_PADRAO);
        Instant inicioHoje = hoje.atStartOfDay(ZONA_PADRAO).toInstant();
        Instant fimHoje = hoje.plusDays(1).atStartOfDay(ZONA_PADRAO).minusNanos(1).toInstant();

        List<Pedido> pedidosHoje = pedidoRepository.findByLojaIdAndPeriodo(lojaId, inicioHoje, fimHoje);

        BigDecimal faturamentoHoje = pedidosHoje.stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .map(Pedido::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pedidosConcluidosHoje = pedidosHoje.stream().filter(p -> p.getStatus() == StatusPedido.ENTREGUE).count();

        long pedidosEmPreparo = pedidoRepository.countByLojaIdAndStatusIn(lojaId, STATUS_EM_PREPARO);
        long pedidosACaminho = pedidoRepository.countByLojaIdAndStatusIn(lojaId, STATUS_A_CAMINHO);

        List<FaturamentoDiaDTO> faturamentoUltimos7Dias = calcularFaturamentoDiario(lojaId, hoje, 7);

        List<Pedido> recentes = pedidoRepository.findTop5ByLojaIdOrderByCriadoEmDesc(lojaId);

        return new PainelResumoDTO(
                loja.isAberto(),
                faturamentoHoje,
                pedidosEmPreparo,
                pedidosACaminho,
                pedidosConcluidosHoje,
                faturamentoUltimos7Dias,
                pedidoResumoLojistaMapper.mapear(recentes)
        );
    }

    private List<FaturamentoDiaDTO> calcularFaturamentoDiario(String lojaId, LocalDate ultimoDia, int quantidadeDias) {
        LocalDate primeiroDia = ultimoDia.minusDays(quantidadeDias - 1L);
        Instant inicio = primeiroDia.atStartOfDay(ZONA_PADRAO).toInstant();
        Instant fim = ultimoDia.plusDays(1).atStartOfDay(ZONA_PADRAO).minusNanos(1).toInstant();

        List<Pedido> pedidosDoPeriodo = pedidoRepository.findByLojaIdAndPeriodo(lojaId, inicio, fim);

        Map<LocalDate, BigDecimal> valorPorDia = pedidosDoPeriodo.stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .collect(Collectors.groupingBy(
                        p -> p.getCriadoEm().atZone(ZONA_PADRAO).toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Pedido::getValorTotal, BigDecimal::add)
                ));

        return primeiroDia.datesUntil(ultimoDia.plusDays(1))
                .map(dia -> new FaturamentoDiaDTO(dia, valorPorDia.getOrDefault(dia, BigDecimal.ZERO)))
                .toList();
    }
}
