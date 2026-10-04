package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.exceptions.PagamentoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixConsultaResilienciaTest {
    @Mock PedidoRepository repository;
    @Mock AsaasPaymentService asaas;
    @InjectMocks PedidoService service;

    private Pedido pedido(String cobranca) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-pix");
        pedido.setUsuarioId("cliente");
        pedido.setFormaPagamento("PIX");
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setPagamentoExpiraEm(Instant.now().plusSeconds(600));
        pedido.setAsaasPaymentId(cobranca);
        when(repository.findById("pedido-pix")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    @Test void timeoutRecuperandoCobrancaMantemPedidoEPagamentoIndisponivel() {
        Pedido original = pedido(null);
        when(asaas.recuperarCobranca(any())).thenThrow(new ResourceAccessException("timeout"));
        var erro = assertThrows(PagamentoIndisponivelException.class,
                () -> service.buscarPagamento("pedido-pix", "cliente"));
        assertEquals(409, erro.getHttpStatus().value());
        assertEquals(StatusPedido.PENDENTE, original.getStatus());
        verify(repository, never()).save(any());
        verify(asaas, never()).criarCobrancaPix(any(), any(), any(), any());
    }

    @Test void falhaQrCodeNaoCriaOutraCobranca() {
        Pedido original = pedido("pay-existente");
        when(asaas.obterCodigoPix(any())).thenThrow(new IllegalStateException("payload vazio"));
        assertThrows(PagamentoIndisponivelException.class,
                () -> service.buscarPagamento("pedido-pix", "cliente"));
        assertEquals("pay-existente", original.getAsaasPaymentId());
        verify(asaas, never()).recuperarCobranca(any());
        verify(repository, never()).save(any());
    }
}
