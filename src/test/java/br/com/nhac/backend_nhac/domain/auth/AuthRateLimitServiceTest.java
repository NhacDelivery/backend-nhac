package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.exceptions.TentativasLoginExcedidasException;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitServiceTest {
    @Test
    void chamadasConcorrentesRespeitamOLimite() throws Exception {
        var service = new AuthRateLimitService();
        try (var executor = Executors.newFixedThreadPool(20)) {
            var inicio = new CountDownLatch(1);
            var tarefas = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) tarefas.add(executor.submit(() -> {
                inicio.await();
                try { service.checar("login"); return true; }
                catch (TentativasLoginExcedidasException e) { return false; }
            }));
            inicio.countDown();
            int aceitas = 0;
            for (var tarefa : tarefas) if (tarefa.get(10, TimeUnit.SECONDS)) aceitas++;
            assertEquals(AuthRateLimitService.LIMITE, aceitas);
        }
    }
}
