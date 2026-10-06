package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoResumoDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
@RestController @RequestMapping("/api/v1")
public class AvaliacaoProdutoController {
    private final AvaliacaoProdutoService service;
    public AvaliacaoProdutoController(AvaliacaoProdutoService service) { this.service=service; }
    @GetMapping("/pedidos/{pedidoId}/avaliacoes-produtos")
    public java.util.List<AvaliacaoProdutoResponseDTO> minhas(@PathVariable String pedidoId, @AuthenticationPrincipal Usuario usuario) {
        return service.minhas(pedidoId,usuario);
    }
    @PostMapping("/produtos/{id}/avaliacoes")
    public AvaliacaoProdutoResponseDTO criar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody AvaliacaoProdutoDTO dto) {
        return service.criar(id,usuario,dto);
    }
    @GetMapping("/produtos/{id}/avaliacoes")
    public Page<AvaliacaoProdutoResponseDTO> listar(@PathVariable String id, @RequestParam(defaultValue="false") boolean fotos,
        @RequestParam(defaultValue="false") boolean positivas, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return service.listar(id,fotos,positivas,page,size);
    }
    @GetMapping("/lojas/{id}/catalogo")
    public Page<ProdutoResumoDTO> catalogo(@PathVariable String id, @RequestParam(defaultValue="Todos") String filtro,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return service.catalogo(id,filtro,page,size);
    }
}
