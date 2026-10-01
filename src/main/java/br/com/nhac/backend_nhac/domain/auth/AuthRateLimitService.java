package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.exceptions.TentativasLoginExcedidasException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {

    public static final int LIMITE = 10;
    public static final Duration JANELA = Duration.ofMinutes(15);
eihroweuihfroiewhoidwheiewnuoaaaaaaaaaaaaaaa
    private final ConcurrentHashMap<String, Janela> tentativas = new ConcurrentHashMap<>();
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
        Janela atual = tentativas.get(chave);
        if (atual == null || !agora.isBefore(atual.inicio.plus(JANELA))) {
            tentativas.put(chave, new Janela(agora, 1));
            return;
        }
        if (atual.contador >= LIMITE) {
            throw new TentativasLoginExcedidasException(identificador);
        }
        tentativas.put(chave, new Janela(atual.inicio, atual.contador + 1));
    }

    private String montarChave(String identificador) {
        return extrairIp() + "|" + identificador;
    }

    private String extrairIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            String forwarded = servletAttrs.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String remote = servletAttrs.getRequest().getRemoteAddr();
            return remote != null ? remote : "unknown";
        }
        return "unknown";
    }

    private record Janela(Instant inicio, int contador) {
    }
}
