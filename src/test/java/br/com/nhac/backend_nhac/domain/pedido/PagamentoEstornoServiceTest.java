package br.com.nhac.backend_nhac.domain.pedido;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoEstornoServiceTest {
    @Mock PedidoRepository repository;
    @Mock AsaasPaymentService asaas;
    @Mock StripePaymentService stripe;
    @Mock PagamentoMetricas metricas;
    @InjectMocks PagamentoEstornoService service;
    Pedido pedido(StatusEstorno status) {
        var p = new Pedido(); p.setId("pedido"); p.setEstornoStatus(status); p.setAsaasPaymentId("pay"); return p;
    }
    @Test void persisteClaimAntesDoDebitoExterno() {
        var p = pedido(StatusEstorno.SOLICITADO);
        when(repository.transicionarEstorno(eq("pedido"), eq(StatusEstorno.SOLICITADO), eq(StatusEstorno.ENVIANDO), any())).thenReturn(1);
        when(asaas.estornar(p)).thenReturn(StatusEstorno.CONCLUIDO);
        service.processar(p);
        var ordem = inOrder(repository, asaas);
        ordem.verify(repository).transicionarEstorno(eq("pedido"), eq(StatusEstorno.SOLICITADO), eq(StatusEstorno.ENVIANDO), any());
        ordem.verify(asaas).estornar(p);
        ordem.verify(repository).transicionarEstorno(eq("pedido"), eq(StatusEstorno.ENVIANDO), eq(StatusEstorno.CONCLUIDO), any());
    }
    @Test void outraInstanciaNaoPodeEnviarMesmoEstorno() {
        service.processar(pedido(StatusEstorno.SOLICITADO));
        verifyNoInteractions(asaas, stripe, metricas);
    }
    @Test void timeoutNaoRepetePostERecuperaPelaConsulta() {
        var p = pedido(StatusEstorno.SOLICITADO);
        when(repository.transicionarEstorno(eq("pedido"), eq(StatusEstorno.SOLICITADO), eq(StatusEstorno.ENVIANDO), any())).thenReturn(1);
        when(asaas.estornar(p)).thenThrow(new IllegalStateException("timeout"));
        service.processar(p);
        verify(repository).transicionarEstorno(eq("pedido"), eq(StatusEstorno.ENVIANDO), eq(StatusEstorno.REVISAO), any());
        p.setEstornoStatus(StatusEstorno.REVISAO);
        when(asaas.consultarEstorno(p)).thenReturn(StatusEstorno.CONCLUIDO);
        service.processar(p);
        verify(asaas, times(1)).estornar(p);
        verify(asaas).consultarEstorno(p);
        verify(metricas).registrar("estorno_enviar", "falha");
    }
    @Test void reinicioAposClaimSomenteConsultaSemRepetirCriacao() {
        var p = pedido(StatusEstorno.ENVIANDO); p.setAsaasPaymentId(null); p.setStripePaymentIntentId("pi");
        when(stripe.consultarEstorno(p)).thenReturn(StatusEstorno.PROCESSANDO);
        service.processar(p);
        verify(stripe).consultarEstorno(p); verify(stripe, never()).estornar(any());
    }
    @Test void filaLimitaLoteESelecionaTodosEstadosRecuperaveis() {
        when(repository.findTop50ByEstornoStatusInAndEstornoAtualizadoEmBeforeOrderByEstornoAtualizadoEmAsc(anyList(), any()))
            .thenReturn(List.of());
        service.processarFila();
        verify(repository).findTop50ByEstornoStatusInAndEstornoAtualizadoEmBeforeOrderByEstornoAtualizadoEmAsc(
            eq(List.of(StatusEstorno.SOLICITADO, StatusEstorno.ENVIANDO, StatusEstorno.PROCESSANDO, StatusEstorno.REVISAO)), any(Instant.class));
    }
}
