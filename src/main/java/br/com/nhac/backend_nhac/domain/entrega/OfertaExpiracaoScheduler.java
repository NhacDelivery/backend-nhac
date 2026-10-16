package br.com.nhac.backend_nhac.domain.entrega;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marca ofertas pendentes vencidas como EXPIRADA a cada 30 segundos. */
@Component
public class OfertaExpiracaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(OfertaExpiracaoScheduler.class);

    private final DespachoService despachoService;

    public OfertaExpiracaoScheduler(DespachoService despachoService) {
        this.despachoService = despachoService;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void expirarOfertas() {
        try {
            int total = despachoService.expirarOfertasVencidas();
            if (total > 0) {
                log.debug("{} oferta(s) de entrega marcada(s) como EXPIRADA.", total);
            }
        } catch (Exception e) {
            // Nunca deixa a exceção subir: o agendador do Spring desliga a task
            // depois de um erro não tratado dependendo da configuração.
            log.error("Falha ao expirar ofertas de entrega: {}", e.getMessage());
        }
    }
}
