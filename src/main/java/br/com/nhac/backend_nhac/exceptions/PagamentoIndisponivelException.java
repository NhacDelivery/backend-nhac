package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;

public class PagamentoIndisponivelException extends NhacException {
    public PagamentoIndisponivelException(String mensagem) {
        super(mensagem, ErrorCode.PAGAMENTO_INDISPONIVEL);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
