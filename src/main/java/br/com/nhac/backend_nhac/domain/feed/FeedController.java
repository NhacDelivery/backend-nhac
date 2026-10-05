package br.com.nhac.backend_nhac.domain.feed;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/feed/posts")
@Tag(name = "Feed", description = "Posts, comentários, curtidas e salvos do feed")
public class FeedController {
    private final FeedService service;
    private final FeedTentativaService tentativas;
    public FeedController(FeedService service, FeedTentativaService tentativas) { this.service = service; this.tentativas = tentativas; }

    @GetMapping
    public Page<FeedPostResponseDTO> listar(@AuthenticationPrincipal Usuario usuario,
            @RequestParam(defaultValue = "Destaques") String categoria,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(usuario, categoria, page, size);
    }
    @GetMapping("/salvos")
    public Page<FeedPostResponseDTO> salvos(@AuthenticationPrincipal Usuario usuario,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listarSalvos(usuario, page, size);
    }
    @GetMapping("/{id}")
    public FeedPostResponseDTO buscar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        return service.buscar(id, usuario);
    }
    @PostMapping
    public ResponseEntity<FeedPostResponseDTO> criar(@AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody FeedPostCreateDTO dto,
            @RequestHeader(value="Idempotency-Key", required=false) String chave) {
        String id = tentativas.executar(usuario, "post", chave, dto, () -> service.criar(usuario, dto).id());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.buscar(id, usuario));
    }
    @PutMapping("/{id}")
    public FeedPostResponseDTO atualizar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody FeedPostCreateDTO dto) {
        return service.atualizar(id, usuario, dto);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        service.remover(id, usuario);
    }
    @PutMapping("/{id}/curtida")
    public FeedPostResponseDTO curtir(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        return service.interagir(id, usuario, FeedInteracao.Tipo.CURTIDA, true);
    }
    @DeleteMapping("/{id}/curtida")
    public FeedPostResponseDTO descurtir(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        return service.interagir(id, usuario, FeedInteracao.Tipo.CURTIDA, false);
    }
    @PutMapping("/{id}/salvo")
    public FeedPostResponseDTO salvar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        return service.interagir(id, usuario, FeedInteracao.Tipo.SALVO, true);
    }
    @DeleteMapping("/{id}/salvo")
    public FeedPostResponseDTO dessalvar(@PathVariable String id, @AuthenticationPrincipal Usuario usuario) {
        return service.interagir(id, usuario, FeedInteracao.Tipo.SALVO, false);
    }
    @GetMapping("/{id}/comentarios")
    public Page<FeedComentarioResponseDTO> comentarios(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue="Padrao") String ordem, @RequestParam(defaultValue="false") boolean autor, @AuthenticationPrincipal Usuario usuario) {
        return service.listarComentarios(id, page, size, ordem, autor, usuario);
    }
    @PostMapping("/{id}/comentarios")
    public ResponseEntity<FeedComentarioResponseDTO> comentar(@PathVariable String id,
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody FeedComentarioCreateDTO dto,
            @RequestHeader(value="Idempotency-Key", required=false) String chave) {
        String comentarioId = tentativas.executar(usuario, "comentario:"+id, chave, dto, () -> service.comentar(id, usuario, dto).id());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.buscarComentario(id, comentarioId));
    }
    @DeleteMapping("/{id}/comentarios/{comentarioId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerComentario(@PathVariable String id, @PathVariable String comentarioId,
            @AuthenticationPrincipal Usuario usuario) {
        service.removerComentario(id, comentarioId, usuario);
    }
}
