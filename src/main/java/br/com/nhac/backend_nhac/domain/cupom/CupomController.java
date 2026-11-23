package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;

@RestController
@RequestMapping("/api/v1/cupons")
public class CupomController {
    private final CupomService service;
    public CupomController(CupomService service) { this.service = service; }

    @GetMapping
    public List<CupomResponse> listar(@AuthenticationPrincipal Usuario usuario, @RequestParam(required=false) String lojaId) {
        return service.listar(usuario.getId(), lojaId);
    }
    @PostMapping("/boas-vindas")
    public CupomResponse ganhar(@AuthenticationPrincipal Usuario usuario) {
        return service.ganharBoasVindas(usuario.getId());
    }
    @PostMapping("/validar")
    public CupomResponse validar(@AuthenticationPrincipal Usuario usuario, @RequestBody @Valid ValidarCupomDTO dto) {
        return service.validar(usuario.getId(), dto.cupomId(), dto.subtotal(), dto.lojaId());
    }
    public record ValidarCupomDTO(@NotBlank String cupomId,
            @NotNull @DecimalMin("0.01") BigDecimal subtotal, String lojaId) {}
}
