package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RepasseEntregadorController {
  private final RepasseEntregadorService service;
  private final RepasseEntregadorRepository repasses;
  private final PedidoRepository pedidos;
  private final EntregadorService entregadores;

  public RepasseEntregadorController(
      RepasseEntregadorService service,
      RepasseEntregadorRepository repasses,
      PedidoRepository pedidos,
      EntregadorService entregadores) {
    this.service = service;
    this.repasses = repasses;
    this.pedidos = pedidos;
    this.entregadores = entregadores;
  }

  public record ApurarDTO(
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorDevido) {}

  public record PagoDTO(
      @NotBlank @Size(max = 100) String referencia,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valor,
      @NotNull Instant pagoEm) {}

  public record ExtratoDTO(
      String pedidoId,
      BigDecimal freteCalculado,
      BigDecimal valorDevido,
      BigDecimal valorPago,
      String status,
      Instant apuradoEm,
      Instant pagoEm,
      String referencia) {
    static ExtratoDTO de(Pedido p, RepasseEntregador r) {
      return new ExtratoDTO(
          p.getId(),
          p.getTaxaFrete(),
          r == null ? null : r.getValorDevido(),
          r == null ? null : r.getValorPago(),
          r == null ? "NAO_APURADO" : r.getPagoEm() == null ? "PENDENTE" : "PAGO",
          r == null ? null : r.getApuradoEm(),
          r == null ? null : r.getPagoEm(),
          r == null ? null : r.getReferenciaPagamento());
    }
  }

  @GetMapping("/entregador/repasses")
  @PreAuthorize("hasRole('ENTREGADOR')")
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public Page<ExtratoDTO> extrato(
      @AuthenticationPrincipal Usuario usuario, @RequestParam(defaultValue = "0") int page) {
    var entregador = entregadores.buscarPorUsuario(usuario);
    var historico =
        pedidos.findHistoricoDoEntregador(
            entregador.getId(), StatusPedido.ENTREGUE, PageRequest.of(Math.max(0, page), 20));
    var registros =
        repasses.findAllById(historico.stream().map(Pedido::getId).toList()).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    RepasseEntregador::getPedidoId, java.util.function.Function.identity()));
    return historico.map(p -> ExtratoDTO.de(p, registros.get(p.getId())));
  }

  @PutMapping("/suporte/repasses/{id}/apuracao")
  @PreAuthorize(
      "hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and"
          + " principal.lojaVinculadaId == null")
  public RepasseEntregadorResponseDTO apurar(
      @PathVariable String id, @RequestBody @Valid ApurarDTO dto) {
    return RepasseEntregadorResponseDTO.de(service.apurar(id, dto.valorDevido()));
  }

  @PutMapping("/suporte/repasses/{id}/pagamento")
  @PreAuthorize(
      "hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and"
          + " principal.lojaVinculadaId == null")
  public RepasseEntregadorResponseDTO pago(
      @PathVariable String id, @RequestBody @Valid PagoDTO dto) {
    return RepasseEntregadorResponseDTO.de(
        service.registrarPagamento(id, dto.referencia(), dto.valor(), dto.pagoEm()));
  }
}
