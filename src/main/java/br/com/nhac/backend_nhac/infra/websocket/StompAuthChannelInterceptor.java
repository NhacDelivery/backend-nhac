package br.com.nhac.backend_nhac.infra.websocket;

import java.security.Principal;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.nhac.backend_nhac.domain.chat.ChatService;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorService;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.AutoridadesFactory;
import br.com.nhac.backend_nhac.infra.security.TokenService;

/**
 * Autentica a conexão WebSocket dentro do frame STOMP CONNECT, não no
 * handshake HTTP. Motivo: o WebSocket nativo do browser não deixa mandar
 * headers customizados no handshake, mas o protocolo STOMP permite headers
 * arbitrários no CONNECT — por isso o handshake em si (/ws/**) é permitAll no
 * SecurityConfig, e a autenticação de verdade acontece aqui, reaproveitando o
 * mesmo TokenService (JWT) usado pelo SecurityFilter do REST.
 *
 * Cliente deve mandar, no frame CONNECT: header "Authorization: Bearer <token>"
 * (bibliotecas STOMP como @stomp/stompjs e stomp_dart_client suportam isso).
 *
 * Valida SUBSCRIBE para três famílias de tópico — sem isso, qualquer usuário
 * autenticado poderia assinar o tópico de outra pessoa e receber dados alheios
 * em tempo real, já que o broker simples do Spring não filtra por padrão:
 *  - /topic/conversas/{id}            → participante da conversa ou dono/funcionário da loja
 *  - /topic/entregador/{id}/ofertas   → o próprio entregador (V039: DespachoService publica as ofertas aqui)
 *  - /topic/pedidos/{id}/status       → cliente dono, entregador da corrida ou loja do pedido (V039)
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String PREFIXO_TOPICO_CONVERSA = "/topic/conversas/";
    private static final String PREFIXO_TOPICO_ENTREGADOR = "/topic/entregador/";
    private static final String SUFIXO_TOPICO_OFERTAS = "/ofertas";
    private static final String PREFIXO_TOPICO_PEDIDO = "/topic/pedidos/";
    private static final String SUFIXO_TOPICO_STATUS = "/status";

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final AutoridadesFactory autoridadesFactory;
    private final ChatService chatService;
    private final EntregadorService entregadorService;
    private final PedidoRepository pedidoRepository;
    private final LojaAccessService lojaAccessService;

    public StompAuthChannelInterceptor(TokenService tokenService,
                                        UsuarioRepository usuarioRepository,
                                        ChatService chatService,
                                        EntregadorService entregadorService,
                                        PedidoRepository pedidoRepository,
                                        LojaAccessService lojaAccessService,
                                        AutoridadesFactory autoridadesFactory) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.chatService = chatService;
        this.autoridadesFactory = autoridadesFactory;
        this.entregadorService = entregadorService;
        this.pedidoRepository = pedidoRepository;
        this.lojaAccessService = lojaAccessService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            autenticarConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            autorizarSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            exigirUsuario(accessor);
            String destino = accessor.getDestination();
            if (destino == null || !destino.matches("/app/conversas/[^/*?{}]+/enviar"))
                throw new WebSocketAutenticacaoException("Destino de envio não permitido.");
        }

        return message;
    }

    private void autenticarConnect(StompHeaderAccessor accessor) {
        String token = extrairToken(accessor.getFirstNativeHeader("Authorization"));
        Usuario usuario = autenticar(token);

        if (usuario == null) {
            throw new WebSocketAutenticacaoException("Token inválido ou ausente no CONNECT.");
        }

        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, autoridadesFactory.montar(usuario));
        accessor.setUser(authentication);
    }

    private void autorizarSubscribe(StompHeaderAccessor accessor) {
        exigirUsuario(accessor);
        String destino = accessor.getDestination();
        if (destino == null || destino.contains("*") || destino.contains("?") || destino.contains("{"))
            throw new WebSocketAutenticacaoException("Destino de assinatura não permitido.");

        if (destino.startsWith(PREFIXO_TOPICO_CONVERSA)) {
            autorizarConversa(accessor, destino);
        } else if (destino.startsWith(PREFIXO_TOPICO_ENTREGADOR) && destino.endsWith(SUFIXO_TOPICO_OFERTAS)) {
            autorizarOfertasEntregador(accessor, destino);
        } else if (destino.startsWith(PREFIXO_TOPICO_PEDIDO) && destino.endsWith(SUFIXO_TOPICO_STATUS)) {
            autorizarStatusPedido(accessor, destino);
        } else if (!"/user/queue/erros".equals(destino)) {
            throw new WebSocketAutenticacaoException("Destino de assinatura não permitido.");
        }
    }

    private void autorizarConversa(StompHeaderAccessor accessor, String destino) {
        Usuario usuario = exigirUsuario(accessor);
        String conversaId = destino.substring(PREFIXO_TOPICO_CONVERSA.length());
        if (!chatService.podeAcessarConversa(conversaId, usuario)) {
            throw new WebSocketAutenticacaoException("Sem permissão para esta conversa.");
        }
    }

    /**
     * DespachoService publica cada oferta em /topic/entregador/{entregadorId}/ofertas.
     * Sem esta checagem, qualquer conta autenticada podia assinar o canal de
     * outro entregador e ver as corridas oferecidas a ele em tempo real —
     * inclusive dados do pedido (endereço, valor do frete) embutidos no payload.
     */
    private void autorizarOfertasEntregador(StompHeaderAccessor accessor, String destino) {
        Usuario usuario = exigirUsuario(accessor);
        if (usuario.getPapel() == Papel.ADMIN) return;

        String entregadorId = destino.substring(
                PREFIXO_TOPICO_ENTREGADOR.length(),
                destino.length() - SUFIXO_TOPICO_OFERTAS.length());

        boolean ehODono = entregadorService.buscarPorUsuarioOuNulo(usuario)
                .map(e -> e.getId().equals(entregadorId))
                .orElse(false);

        if (!ehODono) {
            throw new WebSocketAutenticacaoException("Sem permissão para este canal de ofertas.");
        }
    }

    /**
     * /topic/pedidos/{pedidoId}/status é usado tanto pelo app do cliente
     * (acompanhar o próprio pedido) quanto, desde a V039, pelo motoboy
     * (confirmar que a coleta/entrega refletiu no status). Autoriza os três
     * lados: cliente dono, entregador da corrida, e dono/funcionário da loja.
     */
    private void autorizarStatusPedido(StompHeaderAccessor accessor, String destino) {
        Usuario usuario = exigirUsuario(accessor);
        if (usuario.getPapel() == Papel.ADMIN) return;

        String pedidoId = destino.substring(
                PREFIXO_TOPICO_PEDIDO.length(),
                destino.length() - SUFIXO_TOPICO_STATUS.length());

        Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
        if (pedido == null) {
            // Não vaza se o pedido existe ou não: nega igual a um acesso indevido.
            throw new WebSocketAutenticacaoException("Sem permissão para este pedido.");
        }

        boolean ehCliente = usuario.getId().equals(pedido.getUsuarioId());
        boolean ehEntregadorDaCorrida = pedido.getEntregador() != null
                && pedido.getEntregador().getUsuario() != null
                && usuario.getId().equals(pedido.getEntregador().getUsuario().getId());
        boolean ehLoja = lojaAccessService.temAcessoALoja(usuario, pedido.getLoja().getId());

        if (!ehCliente && !ehEntregadorDaCorrida && !ehLoja) {
            throw new WebSocketAutenticacaoException("Sem permissão para este pedido.");
        }
    }

    private Usuario exigirUsuario(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new WebSocketAutenticacaoException("SUBSCRIBE sem usuário autenticado.");
        }
        return usuario;
    }

    private Usuario autenticar(String token) {
        if (token == null) {
            return null;
        }
        String usuarioId = tokenService.validarToken(token);
        if (usuarioId == null) {
            return null;
        }
        // Checa isEnabled(): sem isso, um funcionário desativado continuaria
        // usando o WS até o token expirar — mesmo com o SecurityFilter HTTP
        // já bloqueando requisições REST pra ele.
        return usuarioRepository.findById(usuarioId)
                .filter(Usuario::isEnabled)
                .orElse(null);
    }

    private String extrairToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.replace("Bearer ", "");
    }
}
