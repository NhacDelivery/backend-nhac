package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.auth.VerificacaoTelefoneService;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios/me/telefone")
public class TelefoneContaController {
    private final TelefoneContaService service;
    public TelefoneContaController(TelefoneContaService service) { this.service = service; }
    @PutMapping
    public void atualizar(@AuthenticationPrincipal Usuario usuario, @RequestBody @Valid ValidarCodigoSmsDTO dto) {
        service.atualizar(usuario.getId(), dto);
    }
}
