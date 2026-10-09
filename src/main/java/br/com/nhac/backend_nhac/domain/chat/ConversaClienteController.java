package br.com.nhac.backend_nhac.domain.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaClienteResumoDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.EnviarMensagemDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Caixa de conversas, abertura, envio REST, histórico e leitura do cliente. */
@RestController
@RequestMapping("/api/v1/conversas")
@Tag(name = "Chat (cliente)", description = "Conversas com lojas e outros clientes, envio, histórico e leitura")
public class ConversaClienteController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ConversaClienteController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @Operation(summary = "Listar conversas do cliente", description = "Conversas com lojas e outros clientes, mais recentes primeiro. Paginação limitada a 100 itens.")
    @GetMapping
    public ResponseEntity<Page<ConversaClienteResumoDTO>> listar(
            @AuthenticationPrincipal Usuario usuario,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(chatService.listarConversasDoCliente(usuario, pageable));
    }

    @Operation(summary = "Abrir ou obter conversa entre clientes", description = "Uma conversa por par, independentemente de quem iniciou. Apenas clientes ativos; não permite conversar consigo mesmo.")
    @PostMapping("/clientes/{clienteId}")
    public ResponseEntity<ConversaAbertaDTO> obterOuCriarEntreClientes(
            @AuthenticationPrincipal Usuario usuario, @PathVariable String clienteId) {
        return ResponseEntity.ok(new ConversaAbertaDTO(
                chatService.obterOuCriarConversaEntreClientes(clienteId, usuario).getId()));
    }

    @Operation(summary = "Enviar mensagem pelo cliente", description = "Envia para loja ou outro cliente e publica no mesmo tópico WebSocket. clientMessageId opcional permite repetir a requisição sem duplicar a mensagem.")
    @PostMapping("/{conversaId}/mensagens")
    public ResponseEntity<MensagemDTO> enviar(
            @AuthenticationPrincipal Usuario usuario, @PathVariable String conversaId,
            @Valid @RequestBody EnviarMensagemDTO dto) {
        // Aplica as mesmas permissões do histórico antes de reutilizar o envio comum.
        chatService.validarConversaDoCliente(conversaId, usuario);
        MensagemDTO mensagem = chatService.enviarMensagem(conversaId, usuario, dto.conteudo(), dto.clientMessageId());
        messagingTemplate.convertAndSend("/topic/conversas/" + conversaId, mensagem);
        return ResponseEntity.ok(mensagem);
    }

    /**
     * Resposta do POST de abertura.
     *
     * Antes este endpoint devolvia ResponseEntity<String> com o id cru. Como o
     * app manda Accept: application/json, o Spring escolhia application/json
     * como Content-Type e mandava o id sem aspas — `conv_abc` não é JSON
     * válido, então o Dio estourava no jsonDecode e o erro chegava no app sem
     * status HTTP nenhum, mesmo com a conversa já tendo sido criada no banco.
     */
    @Schema(description = "Identificação da conversa aberta ou recuperada")
    public record ConversaAbertaDTO(
            @Schema(description = "ID da conversa", example = "conv_9f2c")
            String id
    ) {}

    @Operation(
            summary = "Abrir ou obter conversa com uma loja",
            description = "Idempotente: se já existir uma conversa entre o cliente autenticado e a loja, retorna o id dela. Apenas CLIENTE pode chamar."
    )
    @PostMapping("/lojas/{lojaId}")
    public ResponseEntity<ConversaAbertaDTO> obterOuCriar(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable @NotBlank String lojaId) {
        Conversa conversa = chatService.obterOuCriarConversa(lojaId, usuarioLogado);
        return ResponseEntity.ok(new ConversaAbertaDTO(conversa.getId()));
    }

    /**
     * Espelha o GET /lojista/conversas/{id}/mensagens, mas autorizando pelo
     * lado do cliente.
     */
    @Operation(
            summary = "Histórico de mensagens da conversa",
            description = "Mensagens da conversa do cliente autenticado, mais recentes primeiro (paginado). Só o cliente dono da conversa pode chamar."
    )
    @GetMapping("/{conversaId}/mensagens")
    public ResponseEntity<Page<MensagemDTO>> listarMensagens(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable String conversaId,
            @PageableDefault(size = 30, sort = "enviadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.listarMensagensDoCliente(conversaId, usuarioLogado, pageable));
    }

    @Operation(
            summary = "Marcar conversa como lida",
            description = "Zera o contador de não lidas do lado do cliente. Só o cliente dono da conversa pode chamar."
    )
    @PatchMapping("/{conversaId}/lida")
    public ResponseEntity<Void> marcarComoLida(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PathVariable String conversaId) {
        chatService.marcarComoLidaPeloCliente(conversaId, usuarioLogado);
        return ResponseEntity.noContent().build();
    }
}
