package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.dto.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.ErroPadraoDTO;
import br.com.nhac.backend_nhac.domain.pedido.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos", description = "Endpoints para o processamento de compras e gestão do carrinho")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }


    @Operation(summary = "Finalizar uma nova compra", description = "Recebe o carrinho de compras do Flutter, valida os itens, amarra à loja e gera um novo pedido no sistema. Envie o header Idempotency-Key para evitar duplicidade: a mesma chave devolve o pedido já criado com HTTP 200. Status serializado no JSON como string do enum (PENDENTE, PAGO, PREPARANDO, SAIU_ENTREGA, ENTREGUE, CANCELADO).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso. Devolve o ID do pedido gerado."),
            @ApiResponse(responseCode = "200", description = "Pedido já existia para a Idempotency-Key enviada; devolve o pedido original sem criar duplicata."),

            @ApiResponse(responseCode = "400", description = "Erro de validação no DTO (ex: carrinho vazio, valores negativos, formato de CEP inválido).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "404", description = "Loja não encontrada ou encontra-se fechada para novos pedidos.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),

            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PostMapping
    public ResponseEntity<PedidoCriadoDTO> criarPedido(
            @RequestBody @Valid PedidoCreateDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        ResultadoCriacaoPedido resultado = pedidoService.finalizarPedido(dto, usuarioLogado, idempotencyKey);
        HttpStatus status = resultado.replay() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(resultado.dto());
    }

    @GetMapping("/ativo")
    public ResponseEntity<PedidoResponseDTO> buscarPedidoAtivo(@AuthenticationPrincipal Usuario usuarioLogado) {
        return pedidoService.buscarPedidoAtivo(usuarioLogado.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/pagamento")
    public ResponseEntity<PagamentoPendenteDTO> buscarPagamento(
            @PathVariable String id, @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(pedidoService.buscarPagamento(id, usuarioLogado.getId()));
    }

    @Operation(summary = "Consultar um pedido", description = "Retorna os detalhes de um pedido específico caso pertença ao usuário logado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado e retornado com sucesso."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado: o pedido pertence a outro usuário.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPedido(
            @PathVariable String id,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        PedidoResponseDTO response = pedidoService.buscarPedido(id, usuarioLogado.getId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar meus pedidos", description = "Retorna uma lista paginada dos pedidos feitos pelo usuário autenticado, ordenada dos mais recentes para os mais antigos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos retornada com sucesso."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping
    public ResponseEntity<Page<PedidoResumoDTO>> listarMeusPedidos(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PageableDefault(sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PedidoResumoDTO> page = pedidoService.listarMeusPedidos(usuarioLogado.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Atualizar status do pedido (Uso interno/admin)", description = "Altera o status do pedido (ex: PREPARANDO, SAIU_ENTREGA, ENTREGUE). Restrita aos papéis ADMIN ou LOJISTA.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro de validação ou regra de negócio", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado: usuário não tem permissão para alterar status deste pedido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOJISTA', 'FUNCIONARIO')")
    public ResponseEntity<Void> atualizarStatus(
            @PathVariable String id,
            @Valid @RequestBody PedidoUpdateStatusDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        pedidoService.atualizarStatus(id, dto.status(), usuarioLogado);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cancelar pedido", description = "Cancela o pedido. Apenas o dono pode cancelar e somente enquanto estiver PENDENTE (antes da confirmação do pagamento).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pedido cancelado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro de negócio (ex: status inválido)"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPedido(
            @PathVariable String id,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        pedidoService.cancelarPedido(id, usuarioLogado.getId());
        return ResponseEntity.noContent().build();
    }
}
