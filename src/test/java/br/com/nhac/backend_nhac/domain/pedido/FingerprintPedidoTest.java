package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintPedidoTest {
    private PedidoCreateDTO pedido(String troco, String cpf, String observacao, String cupom) {
        return new PedidoCreateDTO("loja", "DINHEIRO", observacao, new BigDecimal(troco), cpf, null, cupom,
                List.of(new PedidoCreateDTO.ItemPedidoDTO("produto", "Produto", null, 1)));
    }
    @Test void consideraTrocoEPagador() {
        String base = PedidoService.calcularFingerprint("cliente", pedido("50", "123", "", ""));
        assertNotEquals(base, PedidoService.calcularFingerprint("cliente", pedido("100", "123", "", "")));
        assertNotEquals(base, PedidoService.calcularFingerprint("cliente", pedido("50", "456", "", "")));
        assertEquals(base, PedidoService.calcularFingerprint("cliente", pedido("50.00", "123", "", "")));
    }
    @Test void separadoresEmTextoNaoCriamColisoes() {
        assertNotEquals(PedidoService.calcularFingerprint("cliente", pedido("50", "123", "a||b", "c")),
                PedidoService.calcularFingerprint("cliente", pedido("50", "123", "a", "b||c")));
    }
}
