package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("e2e")
@RequestMapping("/api/v1/pedidos")
public class PagamentoSimuladoController {
    private final PedidoService pedidoService;

    public PagamentoSimuladoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping("/{id}/pagamento/simular")
    public ResponseEntity<Void> simular(@PathVariable String id,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        pedidoService.simularPagamento(id, usuarioLogado.getId());
        return ResponseEntity.noContent().build();
    }
}
