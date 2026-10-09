package br.com.nhac.backend_nhac.domain.feed;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api/v1/feed")
public class FeedDenunciaController {
    private final FeedService feed; private final FeedDenunciaRepository denuncias;
    public FeedDenunciaController(FeedService feed,FeedDenunciaRepository denuncias) { this.feed=feed; this.denuncias=denuncias; }
    public record Pedido(@NotBlank @Size(max=1000) String motivo, String comentarioId) {}
    @PostMapping("/posts/{id}/denuncias") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
    public FeedDenuncia criar(@PathVariable String id,@AuthenticationPrincipal Usuario usuario,@Valid @RequestBody Pedido dto) {
        feed.buscar(id,usuario);
        if(dto.comentarioId()!=null) feed.buscarComentario(id,dto.comentarioId());
        var d=new FeedDenuncia(); d.setId(java.util.UUID.randomUUID().toString()); d.setUsuarioId(usuario.getId());
        d.setPostId(id); d.setComentarioId(dto.comentarioId()); d.setMotivo(dto.motivo().trim());
        return denuncias.save(d);
    }
    @GetMapping("/denuncias") @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.data.domain.Page<FeedDenuncia> listar(@org.springframework.data.web.PageableDefault(size=20,sort="criadoEm",direction=org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable page) {
        return denuncias.findAll(org.springframework.data.domain.PageRequest.of(Math.max(0,page.getPageNumber()),Math.min(50,page.getPageSize()),page.getSort()));
    }
    @PatchMapping("/denuncias/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public FeedDenuncia concluir(@PathVariable String id) {
        var d=denuncias.findById(id).orElseThrow(() -> new br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException("Denúncia não encontrada."));
        d.setStatus("ANALISADA"); return denuncias.save(d);
    }
}
