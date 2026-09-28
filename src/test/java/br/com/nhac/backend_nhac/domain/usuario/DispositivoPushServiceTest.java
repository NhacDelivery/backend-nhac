package br.com.nhac.backend_nhac.domain.usuario;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispositivoPushServiceTest {
    @Test
    void registraEReatribuiTokenDoMesmoDispositivo() {
        var repository = mock(DispositivoPushRepository.class);
        var service = new DispositivoPushService(repository);
        String token = "fcm-do-dispositivo";
        service.registrar("cliente-1", token);
        var captor = org.mockito.ArgumentCaptor.forClass(DispositivoPush.class);
        verify(repository).save(captor.capture());
        assertEquals("cliente-1", captor.getValue().getUsuarioId());
        assertEquals(64, captor.getValue().getTokenHash().length());

        reset(repository);
        when(repository.findByTokenHash(DispositivoPushService.hash(token)))
                .thenReturn(Optional.of(captor.getValue()));
        service.registrar("cliente-2", token);
        assertEquals("cliente-2", captor.getValue().getUsuarioId());
        verify(repository).save(captor.getValue());
    }

    @Test
    void removeSomenteTokenDoUsuarioAutenticado() {
        var repository = mock(DispositivoPushRepository.class);
        new DispositivoPushService(repository).remover("cliente-1", "token");
        verify(repository).deleteByUsuarioIdAndTokenHash("cliente-1", DispositivoPushService.hash("token"));
    }
}
