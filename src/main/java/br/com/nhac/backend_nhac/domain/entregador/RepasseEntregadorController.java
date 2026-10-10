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

  public RepasseEntregadorController(RepasseEntregadorService service) {
    this.service = service;
  }

  public record ApurarDTO(
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valorDevido) {}

  public record PagoDTO(
      @NotBlank @Size(max = 100) String referencia,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal valor,
      @NotNull Instant pagoEm) {}

  @GetMapping("/entregador/repasses")
  @PreAuthorize("hasRole('ENTREGADOR')")
  public Page<ExtratoRepasseDTO> extrato(
      @AuthenticationPrincipal Usuario usuario, @RequestParam(defaultValue = "0") int page) {
    return service.extrato(usuario, page);
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
