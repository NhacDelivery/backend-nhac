package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.entregador.dto.EntregaHistoricoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.GanhosEntregadorDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.FreteHoraDTO;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Histórico de corridas e agregação de ganhos do motoboy.
 *
 * Existe porque as abas "Pedidos" e "Ganhos" do app do entregador estavam 100%
 * mockadas (valores fixos "R$ 0,00" e "0 entregas" na home, e um extrato
 * inteiramente estático) — não havia nenhuma rota no backend capaz de
 * alimentá-las.
 */
@Service
public class GanhosEntregadorService {

    /**
     * Fuso usado pra recortar "hoje" e agrupar por dia. Os Instant são gravados
     * em UTC (a aplicação força TimeZone UTC no boot), então agrupar sem
     * converter jogaria as entregas do começo da noite para o dia seguinte.
     */
    private static final ZoneId FUSO_BR = ZoneId.of("America/Sao_Paulo");

    private final PedidoRepository pedidoRepository;
    private final EntregadorService entregadorService;

    public GanhosEntregadorService(PedidoRepository pedidoRepository, EntregadorService entregadorService) {
        this.pedidoRepository = pedidoRepository;
        this.entregadorService = entregadorService;
    }

    @Transactional(readOnly = true)
    public Page<EntregaHistoricoDTO> listarHistorico(Usuario usuarioLogado, StatusPedido status, Pageable pageable) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);
        return pedidoRepository.findHistoricoDoEntregador(entregador.getId(), status, pageable)
                .map(EntregaHistoricoDTO::new);
    }

    @Transactional(readOnly = true)
    public GanhosEntregadorDTO obterGanhos(Usuario usuarioLogado, PeriodoGanhos periodo) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);

        LocalDate hoje = LocalDate.now(FUSO_BR);
        LocalDate primeiroDia = switch (periodo) {
            case HOJE -> hoje;
            case SETE_DIAS -> hoje.minusDays(6);
            case TRINTA_DIAS -> hoje.minusDays(29);
        };

        Instant inicio = primeiroDia.atStartOfDay(FUSO_BR).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(FUSO_BR).toInstant();

        List<FreteHoraDTO> entregues = pedidoRepository.somarFretesPorHora(
                entregador.getId(), StatusPedido.ENTREGUE, inicio, fim);

        BigDecimal total = entregues.stream()
                .map(FreteHoraDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long quantidade = entregues.stream().mapToLong(FreteHoraDTO::entregas).sum();
        BigDecimal ticketMedio = quantidade == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);

        return new GanhosEntregadorDTO(
                periodo.name(),
                total.setScale(2, RoundingMode.HALF_UP),
                quantidade,
                ticketMedio,
                agruparPorDia(entregues, primeiroDia, hoje)
        );
    }

    /**
     * Devolve a série com TODOS os dias do período, inclusive os zerados — o
     * app desenha um gráfico/lista contínua e não deve precisar preencher
     * buraco de data no cliente.
     */
    private List<GanhosEntregadorDTO.GanhoDiaDTO> agruparPorDia(
            List<FreteHoraDTO> pedidos, LocalDate primeiroDia, LocalDate ultimoDia) {

        Map<LocalDate, BigDecimal> valorPorDia = new LinkedHashMap<>();
        Map<LocalDate, Long> qtdPorDia = new LinkedHashMap<>();

        for (LocalDate d = primeiroDia; !d.isAfter(ultimoDia); d = d.plusDays(1)) {
            valorPorDia.put(d, BigDecimal.ZERO);
            qtdPorDia.put(d, 0L);
        }

        for (FreteHoraDTO p : pedidos) {
            Instant referencia = p.referencia();
            if (referencia == null) continue;

            LocalDate dia = referencia.atZone(FUSO_BR).toLocalDate();
            if (!valorPorDia.containsKey(dia)) continue;

            BigDecimal frete = p.valor();
            valorPorDia.merge(dia, frete, BigDecimal::add);
            qtdPorDia.merge(dia, p.entregas(), Long::sum);
        }

        List<GanhosEntregadorDTO.GanhoDiaDTO> serie = new ArrayList<>();
        valorPorDia.forEach((dia, valor) -> serie.add(new GanhosEntregadorDTO.GanhoDiaDTO(
                dia, valor.setScale(2, RoundingMode.HALF_UP), qtdPorDia.getOrDefault(dia, 0L))));

        serie.sort(Comparator.comparing(GanhosEntregadorDTO.GanhoDiaDTO::data));
        return serie;
    }
}
