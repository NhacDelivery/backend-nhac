package br.com.nhac.backend_nhac.exceptions;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class PedidoAtivoException extends NhacException {
    public PedidoAtivoException(String pedidoId, String status) {
        super("Finalize o pedido atual antes de criar outro.", ErrorCode.PEDIDO_ATIVO,
                Map.of("pedidoId", pedidoId, "status", status));
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
