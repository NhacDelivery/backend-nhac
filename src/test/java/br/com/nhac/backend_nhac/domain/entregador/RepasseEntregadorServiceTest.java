package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepasseEntregadorServiceTest {
    private final RepasseEntregadorRepository repository = mock(RepasseEntregadorRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final RepasseEntregadorService service = new RepasseEntregadorService(repository, pedidos);
    private final BigDecimal devido = new BigDecimal("12.50");

    private RepasseEntregador pendente() {
        var r = new RepasseEntregador(); r.setPedidoId("pedido"); r.setValorDevido(devido);
        when(repository.findLockedById("pedido")).thenReturn(Optional.of(r));
        return r;
    }

    @Test void naoApuraCorridaAindaAtiva() {
        var p = new Pedido(); p.setStatus(StatusPedido.SAIU_ENTREGA);
        when(pedidos.findLockedById("pedido")).thenReturn(Optional.of(p));
        assertThrows(RegraDeNegocioException.class, () -> service.apurar("pedido", devido));
        verify(repository, never()).save(any());
    }

    @Test void rejeitaPagamentoParcialOuDataFutura() {
        var r = pendente();
        assertThrows(RegraDeNegocioException.class, () -> service.registrarPagamento("pedido", "ref", new BigDecimal("5.00"), Instant.now().minusSeconds(10)));
        assertThrows(RegraDeNegocioException.class, () -> service.registrarPagamento("pedido", "ref", devido, Instant.now().plusSeconds(3600)));
        assertNull(r.getPagoEm()); assertEquals(BigDecimal.ZERO, r.getValorPago());
        verify(repository, never()).save(any());
    }

    @Test void referenciaDeOutroRepasseNaoPodeSerReutilizada() {
        var r = pendente();
        when(repository.existsByReferenciaPagamento("ref")).thenReturn(true);
        assertThrows(RegraDeNegocioException.class, () -> service.registrarPagamento("pedido", "ref", devido, Instant.now().minusSeconds(10)));
        assertNull(r.getReferenciaPagamento());
    }

    @Test void pagamentoConfirmadoEhIdempotenteESemSobrescrita() {
        var r = pendente(); var data = Instant.now().minusSeconds(10);
        when(repository.save(r)).thenReturn(r);
        assertSame(r, service.registrarPagamento("pedido", "ref", devido, data));
        assertSame(r, service.registrarPagamento("pedido", "ref", devido, data));
        assertThrows(RegraDeNegocioException.class, () -> service.registrarPagamento("pedido", "outra-ref", devido, data));
        verify(repository, times(1)).save(r);
        assertEquals(devido, r.getValorPago()); assertEquals(data, r.getPagoEm());
    }
}
