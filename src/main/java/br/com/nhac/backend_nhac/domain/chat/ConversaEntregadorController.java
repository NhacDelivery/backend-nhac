package br.com.nhac.backend_nhac.domain.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nhac.backend_nhac.domain.chat.ConversaClienteController.ConversaAbertaDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaClienteResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

/**
 * Endpoints do app do entregador para o chat com a loja (V039). Espelha
 * ConversaClienteController — mesma resposta ConversaAbertaDTO (reaproveitada
 * daquele controller, mesmo formato {id}), mesmo formato de erro, mesmo
 * comportamento idempotente na abertura.
 *
 *  - POST  /entregador/conversas/lojas/{lojaId}          → abre/obtém a conversa com a loja
 *  - GET   /entregador/conversas/{conversaId}/mensagens  → histórico da conversa
 *  - PATCH /entregador/conversas/{conversaId}/lida       → marca a conversa como lida pelo entregador
 *
 * O envio de mensagem é via WebSocket (ChatWebSocketController — o mesmo
 * endpoint /app/conversas/{id}/enviar usado pelo cliente e pela loja: o tipo
 * de remetente é resolvido no ChatService pela relação do usuário com a
 * conversa, não pela rota).
 */
@RestController
@RequestMapping("/api/v1/entregador/conversas")
@Tag(name = "Chat (entregador)", description = "Endpoints do app do motoboy: abrir conversa com a loja, ler o histórico e marcar como lida")
public class ConversaEntregadorController {

    private final ChatService chatService;

    public ConversaEntregadorController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Listar conversas do entregador", description = "Conversas com lojas, mais recentes primeiro. Paginação limitada a 100 itens.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    public ResponseEntity<Page<ConversaClienteResumoDTO>> listar(
            @AuthenticationPrincipal Usuario usuario,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(chatService.listarConversasDoEntregador(usuario, pageable));
    }

    @Operation(
            summary = "Abrir ou obter conversa com a loja",
            description = "Idempotente: se já existir uma conversa entre o entregador autenticado e a loja, retorna o id dela. Apenas ENTREGADOR pode chamar."
    )
    @PostMapping("/lojas/{lojaId}")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    public ResponseEntity<ConversaAbertaDTO> obterOuCriar(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable @NotBlank String lojaId) {
        Conversa conversa = chatService.obterOuCriarConversaEntregador(lojaId, usuarioLogado);
        return ResponseEntity.ok(new ConversaAbertaDTO(conversa.getId()));
    }

    @Operation(
            summary = "Histórico de mensagens da conversa",
            description = "Mensagens da conversa do entregador autenticado, mais recentes primeiro (paginado). Só o entregador dono da conversa pode chamar."
    )
    @GetMapping("/{conversaId}/mensagens")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    public ResponseEntity<Page<MensagemDTO>> listarMensagens(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable String conversaId,
            @PageableDefault(size = 30, sort = "enviadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.listarMensagensDoEntregador(conversaId, usuarioLogado, pageable));
    }

    @Operation(
            summary = "Marcar conversa como lida",
            description = "Zera o contador de não lidas do lado do entregador. Só o entregador dono da conversa pode chamar."
    )
    @PatchMapping("/{conversaId}/lida")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    public ResponseEntity<Void> marcarComoLida(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable String conversaId) {
        chatService.marcarComoLidaPeloEntregador(conversaId, usuarioLogado);
        return ResponseEntity.noContent().build();
    }
}
