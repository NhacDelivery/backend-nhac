package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;

public class CodigoEntregaObrigatorioException extends NhacException {
    public CodigoEntregaObrigatorioException() {
        super("O código de entrega é obrigatório para concluir a entrega.", ErrorCode.CODIGO_ENTREGA_OBRIGATORIO);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
