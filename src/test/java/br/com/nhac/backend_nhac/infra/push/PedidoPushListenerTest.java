package br.com.nhac.backend_nhac.infra.push;

import br.com.nhac.backend_nhac.domain.pedido.PedidoStatusPushEvent;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.DispositivoPush;
import br.com.nhac.backend_nhac.domain.usuario.DispositivoPushRepository;
import java.util.List;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class PedidoPushListenerTest {
    @Test
    void enviaSomenteParaOsDispositivosDoClienteERevogaTokenExpirado() throws Exception {
        var repository = mock(DispositivoPushRepository.class);
        var sender = mock(FcmSender.class);
        var dispositivo = new DispositivoPush("id", "cliente", "hash", "token");
        when(repository.findByUsuarioId("cliente")).thenReturn(List.of(dispositivo));
        when(sender.enviar("token", "pedido", "SAIU_ENTREGA", "Nhac • Pedido atualizado",
                "Seu pedido saiu para entrega")).thenReturn(false);

        new PedidoPushListener(repository, sender).aoAlterarStatus(
                new PedidoStatusPushEvent("pedido", "cliente", StatusPedido.SAIU_ENTREGA));

        verify(repository).findByUsuarioId("cliente");
        verify(repository).delete(dispositivo);
    }
}
