package br.com.nhac.backend_nhac.infra.security;

import br.com.nhac.backend_nhac.infra.websocket.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;

class StompPublicacaoSecurityTest {
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(null, null, null, null, null, null, null);

    private org.springframework.messaging.Message<byte[]> mensagem(StompCommand comando, String destino) {
        var accessor = StompHeaderAccessor.create(comando);
        accessor.setDestination(destino);
        accessor.setUser(new UsernamePasswordAuthenticationToken(Usuario.builder().id("cliente").build(), null));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/pedidos/alheio/status", "/topic/conversas/alheia", "/queue/erros", "/app/desconhecido"})
    void clienteNaoPublicaComoServidor(String destino) {
        assertThrows(WebSocketAutenticacaoException.class, () -> interceptor.preSend(mensagem(StompCommand.SEND, destino), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/**", "/queue/**", "/topic/desconhecido"})
    void assinaturaDesconhecidaOuComCuringaEhNegada(String destino) {
        assertThrows(WebSocketAutenticacaoException.class, () -> interceptor.preSend(mensagem(StompCommand.SUBSCRIBE, destino), null));
    }

    @Test
    void preservaEnvioPelaAplicacaoEFilaPessoalDeErros() {
        assertDoesNotThrow(() -> interceptor.preSend(mensagem(StompCommand.SEND, "/app/conversas/conversa/enviar"), null));
        assertDoesNotThrow(() -> interceptor.preSend(mensagem(StompCommand.SUBSCRIBE, "/user/queue/erros"), null));
    }
}
