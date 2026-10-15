package br.com.nhac.backend_nhac.domain.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.junit.jupiter.api.Assertions.*;

class PagamentoOperacaoTest {
    @Test void dinheiroNuncaTemEstornoEletronico() {
        var p = new Pedido(); p.setStatus(StatusPedido.PAGO); p.setFormaPagamento("DINHEIRO");
        assertFalse(PagamentoOperacaoDTO.de(p).podeSolicitarEstorno());
        p.setAsaasPaymentId("dado-inconsistente");
        assertFalse(PagamentoOperacaoDTO.de(p).podeSolicitarEstorno());
    }
    @Test void elegibilidadeExigeCobrancaUnicaCorrespondenteAFormaEEstadoPago() {
        var p = new Pedido(); p.setStatus(StatusPedido.PAGO); p.setFormaPagamento("PIX"); p.setAsaasPaymentId("pay");
        assertTrue(PagamentoOperacaoDTO.de(p).podeSolicitarEstorno());
        p.setStripePaymentIntentId("pi"); assertFalse(PagamentoOperacaoDTO.de(p).podeSolicitarEstorno());
        p.setStripePaymentIntentId(null); p.setPagamentoCriacaoIncerta(true);
        assertFalse(PagamentoOperacaoDTO.de(p).podeSolicitarEstorno());
    }
    @Test void pendenciaVencidaOuIncertaEConciliaNaoPagamentoFalho() {
        var p = new Pedido(); p.setStatus(StatusPedido.PENDENTE); p.setPagamentoExpiraEm(Instant.now().minusSeconds(10));
        assertEquals("CONCILIACAO", PagamentoOperacaoDTO.de(p).estado());
        p.setEstornoStatus(StatusEstorno.REVISAO);
        assertEquals("ESTORNO_REVISAO", PagamentoOperacaoDTO.de(p).estado());
    }
    @Test void metricasContamFalhasSemIdentificadoresPessoais() {
        var registry = new SimpleMeterRegistry(); var metricas = new PagamentoMetricas(registry);
        metricas.registrar("conciliacao", "falha"); metricas.registrar("conciliacao", "falha");
        assertEquals(2, metricas.leituras().getFirst().total());
        assertEquals(2, registry.getMeters().getFirst().getId().getTags().size());
    }
}
