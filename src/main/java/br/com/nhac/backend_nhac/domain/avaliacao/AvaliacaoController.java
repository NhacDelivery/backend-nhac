package br.com.nhac.backend_nhac.domain.avaliacao;

import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.ErroPadraoDTO;
import br.com.nhac.backend_nhac.domain.avaliacao.AvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Avaliações", description = "Endpoints para gerenciamento de avaliações de lojas e pedidos")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @Operation(summary = "Criar uma avaliação para um pedido", description = "Permite que o usuário logado avalie um pedido que ele mesmo realizou (deve estar ENTREGUE ou CONCLUIDO).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Avaliação criada com sucesso."),
            @ApiResponse(responseCode = "400", description = "Erro de validação nos dados ou regra de negócio (pedido não entregue, já avaliado, etc).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou usuário não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PostMapping("/avaliacoes")
    public ResponseEntity<AvaliacaoResumoDTO> criarAvaliacao(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AvaliacaoCreateDTO dto) {

        AvaliacaoResumoDTO novaAvaliacao = avaliacaoService.criarAvaliacao(usuario.getId(), dto);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(novaAvaliacao.id())
                .toUri();

        return ResponseEntity.created(uri).body(novaAvaliacao);
    }

    @Operation(summary = "Listar avaliações de uma loja", description = "Retorna uma lista paginada com as avaliações feitas para uma loja específica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Listagem realizada com sucesso."),
            @ApiResponse(responseCode = "404", description = "Loja não encontrada.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/lojas/{lojaId}/avaliacoes")
    public ResponseEntity<Page<AvaliacaoResumoDTO>> listarAvaliacoesPorLoja(
            @PathVariable String lojaId,
            Pageable pageable) {

        Page<AvaliacaoResumoDTO> avaliacoes = avaliacaoService.listarAvaliacoesPorLoja(lojaId, pageable);
        return ResponseEntity.ok(avaliacoes);
    }

    @GetMapping("/lojas/{lojaId}/avaliacoes/resumo")
    public java.util.Map<String,Object> resumoLoja(@PathVariable String lojaId) { return avaliacaoService.resumoLoja(lojaId); }
}
