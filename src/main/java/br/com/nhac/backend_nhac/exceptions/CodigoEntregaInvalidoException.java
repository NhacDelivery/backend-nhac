package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class CodigoEntregaInvalidoException extends NhacException {
    public CodigoEntregaInvalidoException(int tentativasRestantes) {
        super("Código de entrega inválido.", ErrorCode.CODIGO_ENTREGA_INVALIDO,
                Map.of("tentativasRestantes", tentativasRestantes));
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
