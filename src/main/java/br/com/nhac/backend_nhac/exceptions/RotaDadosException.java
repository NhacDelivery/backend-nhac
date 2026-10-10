package br.com.nhac.backend_nhac.exceptions;
import org.springframework.http.HttpStatus;
public class RotaDadosException extends NhacException {
    public RotaDadosException(String mensagem) { super(mensagem, ErrorCode.ROTA_DADOS_INCOMPLETOS); }
    @Override public HttpStatus getHttpStatus() { return HttpStatus.UNPROCESSABLE_ENTITY; }
}
