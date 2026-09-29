package br.com.nhac.backend_nhac.domain.pedido;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoExpiracaoServiceTest {
    @Mock PedidoRepository repository;
    @Mock PedidoService pedidoService;
    @Mock AsaasPaymentService asaas;
    @Mock StripePaymentService stripe;
    @InjectMocks PagamentoExpiracaoService service;

    private Pedido pixVencido() {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setFormaPagamento("PIX");
        pedido.setAsaasPaymentId("pay-1");
        pedido.setPagamentoExpiraEm(Instant.now().minusSeconds(1));
        return pedido;
    }

    @Test
    void cancelaSomenteAposEncerrarCobranca() {
        when(repository.findByStatusAndPagamentoExpiraEmLessThanEqual(eq(StatusPedido.PENDENTE), any()))
                .thenReturn(List.of(pixVencido()));
        when(asaas.consultarStatus("pay-1")).thenReturn("PENDING");

        service.expirarPendentes();

        var ordem = inOrder(asaas, pedidoService);
        ordem.verify(asaas).cancelarCobranca("pay-1");
        ordem.verify(pedidoService).cancelarPorExpiracao("pedido-1");
    }

    @Test
    void confirmaPagamentoRecebidoAntesDeCancelar() {
        when(repository.findByStatusAndPagamentoExpiraEmLessThanEqual(eq(StatusPedido.PENDENTE), any()))
                .thenReturn(List.of(pixVencido()));
        when(asaas.consultarStatus("pay-1")).thenReturn("RECEIVED");

        service.expirarPendentes();

        verify(pedidoService).marcarComoPagoPorAsaasPaymentId("pay-1");
        verify(asaas, never()).cancelarCobranca(anyString());
        verify(pedidoService, never()).cancelarPorExpiracao(anyString());
    }

    @Test
    void falhaDoProvedorMantemPedidoAtivo() {
        when(repository.findByStatusAndPagamentoExpiraEmLessThanEqual(eq(StatusPedido.PENDENTE), any()))
                .thenReturn(List.of(pixVencido()));
        when(asaas.consultarStatus("pay-1")).thenThrow(new IllegalStateException("indisponível"));

        service.expirarPendentes();

        verify(pedidoService, never()).cancelarPorExpiracao(anyString());
    }
}
