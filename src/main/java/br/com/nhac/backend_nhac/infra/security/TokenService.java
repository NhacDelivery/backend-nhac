package br.com.nhac.backend_nhac.infra.security;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;
    private final Clock clock;

    public TokenService() { this(Clock.systemUTC()); }
    TokenService(Clock clock) { this.clock = clock; }

    public String gerarToken(Usuario usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("nhac-api")
                    .withSubject(usuario.getId())
                    .withClaim("id", usuario.getId())
                    .withExpiresAt(gerarDataExpiracao())
                    .sign(algorithm);
        } catch (JWTCreationException jwtCreationException) {
            throw new RuntimeException("Erro ao gerar token", jwtCreationException);
        }
    }
    public String validarToken(String token){
        Algorithm algorithm = Algorithm.HMAC256(secret);
        try{
            return JWT.require(algorithm)
                    .withIssuer("nhac-api")
                    .build()
                    .verify(token)
                    .getSubject();

        } catch (JWTVerificationException e) {
            return null;
        }
    }

    private Instant gerarDataExpiracao() {
        return Instant.now(clock).plus(Duration.ofDays(30));
    }


}
