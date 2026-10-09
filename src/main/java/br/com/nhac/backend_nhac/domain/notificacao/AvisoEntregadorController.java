package br.com.nhac.backend_nhac.domain.notificacao;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
@RestController @RequestMapping("/api/v1/entregador/avisos") @PreAuthorize("hasRole('ENTREGADOR')")
public class AvisoEntregadorController {
    private final AvisoEntregadorRepository avisos;
    public AvisoEntregadorController(AvisoEntregadorRepository avisos) { this.avisos=avisos; }
    @GetMapping public Page<AvisoEntregador> listar(@AuthenticationPrincipal Usuario usuario,@RequestParam(defaultValue="0") int page) {
        return avisos.findByUsuarioIdOrderByCriadoEmDesc(usuario.getId(),PageRequest.of(Math.max(0,page),20));
    }
}
