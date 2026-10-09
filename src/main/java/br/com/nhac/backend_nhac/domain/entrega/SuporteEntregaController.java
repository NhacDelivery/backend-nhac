package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.SuporteEntregaResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SuporteEntregaController {
  private final SuporteEntregaService service;
  private final SolicitacaoSuporteRepository repository;

  public SuporteEntregaController(
      SuporteEntregaService service, SolicitacaoSuporteRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  public record AbrirDTO(
      @NotBlank
          @Pattern(
              regexp =
                  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
          String id,
      @NotBlank @Pattern(regexp = "RETIRADA|TRANSFERENCIA|ACIDENTE|ENDERECO|OUTRO") String motivo,
      @NotBlank @Size(min = 5, max = 2000) String descricao) {}

  public record RespostaDTO(@NotBlank @Size(min = 5, max = 2000) String resposta) {}

  @PostMapping("/entregas/{pedidoId}/suporte")
  @PreAuthorize("hasRole('ENTREGADOR')")
  public SuporteEntregaResponseDTO abrir(
      @PathVariable String pedidoId,
      @AuthenticationPrincipal Usuario usuario,
      @RequestBody @Valid AbrirDTO dto) {
    return SuporteEntregaResponseDTO.de(service.abrir(pedidoId, usuario, dto));
  }

  @GetMapping("/entregas/{pedidoId}/suporte")
  @PreAuthorize("hasRole('ENTREGADOR')")
  public List<SuporteEntregaResponseDTO> listar(
      @PathVariable String pedidoId, @AuthenticationPrincipal Usuario usuario) {
    return service.listar(pedidoId, usuario).stream().map(SuporteEntregaResponseDTO::de).toList();
  }

  @GetMapping("/suporte/entregas")
  @PreAuthorize(
      "hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and"
          + " principal.lojaVinculadaId == null")
  public Page<SuporteEntregaResponseDTO> fila(
      @RequestParam(defaultValue = "ABERTO") String status,
      @RequestParam(defaultValue = "0") int page) {
    return repository
        .findByStatusOrderByCriadoEmAsc(status, PageRequest.of(Math.max(0, page), 20))
        .map(SuporteEntregaResponseDTO::de);
  }

  @PutMapping("/suporte/entregas/{id}/resposta")
  @PreAuthorize(
      "hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and"
          + " principal.lojaVinculadaId == null")
  public SuporteEntregaResponseDTO responder(
      @PathVariable String id, @RequestBody @Valid RespostaDTO dto) {
    return SuporteEntregaResponseDTO.de(service.responder(id, dto.resposta()));
  }
}
