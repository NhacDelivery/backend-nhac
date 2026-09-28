package br.com.nhac.backend_nhac.infra.push;

import br.com.nhac.backend_nhac.domain.pedido.PedidoStatusPushEvent;
import br.com.nhac.backend_nhac.domain.usuario.DispositivoPushRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PedidoPushListener {
    private static final Logger log = LoggerFactory.getLogger(PedidoPushListener.class);
    private final DispositivoPushRepository dispositivos;
    private final FcmSender sender;

    public PedidoPushListener(DispositivoPushRepository dispositivos, FcmSender sender) {
        this.dispositivos = dispositivos;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("pushTaskExecutor")
    public void aoAlterarStatus(PedidoStatusPushEvent evento) {
        String texto = switch (evento.status()) {
            case PAGO -> "Pagamento confirmado";
            case PREPARANDO -> "Sua loja começou a preparar o pedido";
            case SAIU_ENTREGA -> "Seu pedido saiu para entrega";
            case ENTREGUE -> "Pedido entregue";
            case CANCELADO -> "Pedido cancelado";
            case PENDENTE -> "Pedido recebido";
        };
        for (var dispositivo : dispositivos.findByUsuarioId(evento.usuarioId())) {
            try {
                if (!sender.enviar(dispositivo.getToken(), evento.pedidoId(),
                        evento.status().name(), "Nhac • Pedido atualizado", texto)) {
                    dispositivos.delete(dispositivo);
                }
            } catch (Exception e) {
                log.warn("Não foi possível enviar push do pedido {}: {}", evento.pedidoId(), e.getMessage());
            }
        }
    }
}
