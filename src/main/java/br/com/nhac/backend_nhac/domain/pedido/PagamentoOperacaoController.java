package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PagamentoOperacaoController {
    private final PedidoRepository repository;
    private final PedidoService service;
    private final PagamentoMetricas metricas;
    private final PagamentoEstornoService estornos;
    public PagamentoOperacaoController(PedidoRepository repository, PedidoService service, PagamentoMetricas metricas, PagamentoEstornoService estornos) {
        this.repository = repository; this.service = service; this.metricas = metricas; this.estornos = estornos;
    }
    @GetMapping("/pedidos/{id}/operacao-pagamento")
    public PagamentoOperacaoDTO consultar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        Pedido p = repository.findById(id).orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (!p.getUsuarioId().equals(usuario.getId())) throw new AcessoNegadoException("Este pedido pertence a outro usuário.");
        return PagamentoOperacaoDTO.de(p);
    }
    @PostMapping("/pedidos/{id}/estorno")
    public ResponseEntity<Void> solicitar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        service.solicitarEstorno(id, usuario.getId());
        return ResponseEntity.accepted().build();
    }
    @GetMapping("/operacao/pagamentos")
    @PreAuthorize("hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and principal.lojaVinculadaId == null")
    public Page<PagamentoOperacaoDTO> fila(@RequestParam(defaultValue = "0") int page) {
        return repository.filaOperacao(Instant.now(), PageRequest.of(Math.max(0, page), 20, Sort.by("criadoEm", "id")))
                .map(PagamentoOperacaoDTO::de);
    }
    @PostMapping("/operacao/pagamentos/processar")
    @PreAuthorize("hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and principal.lojaVinculadaId == null")
    public ResponseEntity<Void> processar() {
        estornos.processarFila();
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/operacao/pagamentos/metricas")
    @PreAuthorize("hasRole('ADMIN') and principal.ativo and principal.papel.name() == 'ADMIN' and principal.lojaVinculadaId == null")
    public java.util.List<PagamentoMetricas.Leitura> metricas() { return metricas.leituras(); }
}
