package br.com.nhac.backend_nhac.exceptions;
import org.springframework.http.HttpStatus;
public class RotaTemporariaException extends NhacException {
    public RotaTemporariaException(String mensagem) { super(mensagem, ErrorCode.ROTA_SERVICO_INDISPONIVEL); }
    @Override public HttpStatus getHttpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
}
