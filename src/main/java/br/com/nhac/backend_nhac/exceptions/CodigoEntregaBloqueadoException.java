package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.Map;

public class CodigoEntregaBloqueadoException extends NhacException {
    public CodigoEntregaBloqueadoException(Instant tentativaLiberadaEm) {
        super("Código de entrega bloqueado por excesso de tentativas.", ErrorCode.CODIGO_ENTREGA_BLOQUEADO,
                Map.of("tentativaLiberadaEm", tentativaLiberadaEm.toString()));
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
