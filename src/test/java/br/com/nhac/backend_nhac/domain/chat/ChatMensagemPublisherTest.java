package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class ChatMensagemPublisherTest {
    @Test
    void falhaDoBrokerAposCommitNaoAnunciaFalhaDaPersistencia() {
        var template = mock(SimpMessagingTemplate.class);
        var dto = new MensagemDTO("msg_1", "conv_1", RemetenteTipo.CLIENTE, "cliente", "Oi", Instant.now());
        doThrow(new MessageDeliveryException("Broker indisponível"))
                .when(template).convertAndSend("/topic/conversas/conv_1", dto);
        assertDoesNotThrow(() -> new ChatMensagemPublisher(template).publicar(new MensagemEnviadaEvent(dto)));
        verify(template).convertAndSend("/topic/conversas/conv_1", dto);
    }
}
