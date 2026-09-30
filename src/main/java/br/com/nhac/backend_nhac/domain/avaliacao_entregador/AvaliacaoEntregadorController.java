package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pedidos")
public class AvaliacaoEntregadorController {

    private final AvaliacaoEntregadorService service;

    public AvaliacaoEntregadorController(AvaliacaoEntregadorService service) {
        this.service = service;
    }

    @PostMapping("/{pedidoId}/avaliacao-entregador")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<AvaliacaoEntregadorResponseDTO> criar(
            @PathVariable String pedidoId,
            @RequestBody @Valid AvaliacaoEntregadorCreateDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        var response = service.criar(pedidoId, dto, usuarioLogado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pedidoId}/avaliacao-entregador")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<AvaliacaoEntregadorResponseDTO> buscar(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return service.buscarPorPedido(pedidoId, usuarioLogado.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
