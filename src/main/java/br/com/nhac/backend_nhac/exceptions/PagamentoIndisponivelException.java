package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;

public class PagamentoIndisponivelException extends NhacException {
    public PagamentoIndisponivelException(String mensagem) {
        super(mensagem, ErrorCode.PAGAMENTO_INDISPONIVEL);
    }

    public PagamentoIndisponivelException(String mensagem, String pedidoId) {
        super(mensagem, ErrorCode.PAGAMENTO_INDISPONIVEL, java.util.Map.of("pedidoId", pedidoId));
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
