package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.EnviarMensagemDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.NhacException;
import br.com.nhac.backend_nhac.infra.websocket.WebSocketAutenticacaoException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.Map;

/**
 * Canal em tempo real do chat. Cliente conecta em /ws (SockJS+STOMP) ou
 * /ws-native (WS puro), autentica no CONNECT (ver StompAuthChannelInterceptor)
 * e:
 *   - envia mensagem publicando em /app/conversas/{conversaId}/enviar
 *   - recebe mensagens novas assinando /topic/conversas/{conversaId}
 *
 * SUBSCRIBE é autorizado pelo StompAuthChannelInterceptor; SEND, pelo ChatService.
 * Chats diretos permitem somente os dois clientes, inclusive para assinatura.
 */
@Controller
@Validated
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/conversas/{conversaId}/enviar")
    public void enviar(@DestinationVariable String conversaId, @jakarta.validation.Valid EnviarMensagemDTO dto, Principal principal) {
        Usuario remetente = extrairUsuario(principal);
        MensagemDTO mensagem = chatService.enviarMensagem(conversaId, remetente, dto.conteudo(), dto.clientMessageId());
        messagingTemplate.convertAndSend("/topic/conversas/" + conversaId, mensagem);
    }

    @MessageExceptionHandler(NhacException.class)
    @SendToUser("/queue/erros")
    public Map<String, String> tratarErroDeNegocio(NhacException e) {
        return Map.of("erro", e.getMessage());
    }

    @MessageExceptionHandler(org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class)
    @SendToUser("/queue/erros")
    public Map<String, String> tratarPayloadInvalido(Exception e) {
        return Map.of("erro", "Mensagem inválida. Envie conteudo com 1 a 4000 caracteres e clientMessageId em formato UUID.");
    }

    private Usuario extrairUsuario(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        // Não deveria acontecer nunca (o interceptor já barra CONNECT sem usuário válido) — é um guarda extra.
        throw new WebSocketAutenticacaoException("Sessão WebSocket sem usuário autenticado.");
    }
}
