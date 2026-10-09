package br.com.nhac.backend_nhac.domain.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatMensagemPublisher {
    private static final Logger log = LoggerFactory.getLogger(ChatMensagemPublisher.class);
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMensagemPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(MensagemEnviadaEvent event) {
        var mensagem = event.mensagem();
        try {
            messagingTemplate.convertAndSend("/topic/conversas/" + mensagem.conversaId(), mensagem);
        } catch (RuntimeException e) {
            // O commit já aconteceu: falha do broker não deve anunciar falha da
            // persistência. A mensagem continua disponível no histórico REST.
            log.warn("Falha ao publicar mensagem de chat já persistida: id={}", mensagem.id(), e);
        }
    }
}
