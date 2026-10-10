package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios/me/telefone")
public class TelefoneContaController {
  private final TelefoneContaService service;

  public TelefoneContaController(TelefoneContaService service) {
    this.service = service;
  }

  @PutMapping
  @PreAuthorize("isAuthenticated()")
  public void atualizar(
      @AuthenticationPrincipal Usuario usuario, @RequestBody @Valid ValidarCodigoSmsDTO dto) {
    if (usuario == null || !usuario.isAtivo()) {
      throw new AcessoNegadoException("Usuário não autenticado.");
    }
    service.atualizar(usuario.getId(), dto);
  }
}
