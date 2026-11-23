package br.com.nhac.backend_nhac.domain.usuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nhac.backend_nhac.domain.usuario.dto.EnderecoUsuarioDTO;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/usuarios/{usuarioId}/enderecos")
public class EnderecoUsuarioController {

    private final UsuarioService usuarioService;

    public EnderecoUsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    private void validarPropriedade(String idNaUrl, Usuario usuarioLogado) {
        if (!idNaUrl.equals(usuarioLogado.getId())) {
            throw new AcessoNegadoException("Acesso negado: não tem permissão para aceder ou modificar os dados de outro utilizador.");
        }
    }



    @GetMapping
    public ResponseEntity<List<EnderecoUsuarioDTO>> buscarEnderecos(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        usuarioService.validarPropriedade(usuarioId, usuarioLogado);
        return ResponseEntity.ok(usuarioService.listarEnderecos(usuarioId));
    }

    @PostMapping
    public ResponseEntity<Void> adicionarEndereco(
            @PathVariable String usuarioId,
            @RequestBody @Valid EnderecoUsuarioDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        validarPropriedade(usuarioId, usuarioLogado);
        usuarioService.adicionarEndereco(usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(params = "somenteSeVazio=true")
    public ResponseEntity<Void> adicionarEnderecoAutomatico(@PathVariable String usuarioId,
            @RequestBody @Valid EnderecoUsuarioDTO dto, @AuthenticationPrincipal Usuario usuarioLogado) {
        validarPropriedade(usuarioId, usuarioLogado);
        usuarioService.adicionarPrimeiroEndereco(usuarioId, dto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{enderecoId}")
    public ResponseEntity<Void> atualizarEndereco(
            @PathVariable String usuarioId,
            @PathVariable String enderecoId,
            @RequestBody @Valid EnderecoUsuarioDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        validarPropriedade(usuarioId, usuarioLogado);
        usuarioService.atualizarEndereco(usuarioId, enderecoId, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{enderecoId}")
    public ResponseEntity<Void> removerEndereco(
            @PathVariable String usuarioId,
            @PathVariable String enderecoId,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        validarPropriedade(usuarioId, usuarioLogado);
        usuarioService.removerEndereco(usuarioId, enderecoId);
        return ResponseEntity.noContent().build();
    }
}
