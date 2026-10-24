package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.exceptions.TentativasLoginExcedidasException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class AuthRateLimitService {

    public static final int LIMITE = 10;
    public static final Duration JANELA = Duration.ofMinutes(15);

    private final Cache<String, Janela> tentativas = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(JANELA).build();
    private final Clock clock;

    public AuthRateLimitService() {
        this(Clock.systemUTC());
    }

    AuthRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void checar(String identificador) {
        String chave = montarChave(identificador);
        Instant agora = Instant.now(clock);
        tentativas.asMap().compute(chave, (ignorada, atual) -> {
            if (atual == null || !agora.isBefore(atual.inicio.plus(JANELA))) return new Janela(agora, 1);
            if (atual.contador >= LIMITE) throw new TentativasLoginExcedidasException(identificador);
            return new Janela(atual.inicio, atual.contador + 1);
        });
    }

    private String montarChave(String identificador) {
        return extrairIp() + "|" + identificador;
    }

    private String extrairIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            // Headers encaminhados só podem ser tratados pelo servidor/proxy confiável.
            String remote = servletAttrs.getRequest().getRemoteAddr();
            return remote != null ? remote : "unknown";
        }
        return "unknown";
    }

    private record Janela(Instant inicio, int contador) {
    }
}
