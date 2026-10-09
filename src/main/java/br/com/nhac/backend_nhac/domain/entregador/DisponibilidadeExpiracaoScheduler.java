package br.com.nhac.backend_nhac.domain.entregador;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisponibilidadeExpiracaoScheduler {
    private final EntregadorRepository repository;
    private final long maxAge;
    public DisponibilidadeExpiracaoScheduler(EntregadorRepository repository,
            @Value("${nhac.entrega.localizacao-max-age-seconds:120}") long maxAge) {
        this.repository = repository;
        this.maxAge = maxAge;
    }
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    @Transactional
    public void expirar() {
        repository.expirarDisponibilidade(Instant.now().minusSeconds(Math.max(30, maxAge)));
    }
}
