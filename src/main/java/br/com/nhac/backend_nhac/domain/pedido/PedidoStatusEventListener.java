package br.com.nhac.backend_nhac.domain.pedido;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PedidoStatusEventListener {
    private final SimpMessagingTemplate messagingTemplate;

    public PedidoStatusEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(PedidoStatusAtualizadoEvent event) {
        messagingTemplate.convertAndSend("/topic/pedidos/" + event.pedidoId() + "/status", event.status().name());
    }
}
