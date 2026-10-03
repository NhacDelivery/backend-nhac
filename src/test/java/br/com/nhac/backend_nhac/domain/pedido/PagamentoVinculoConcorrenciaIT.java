package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.junit.jupiter.api.Assertions.*;

class PagamentoVinculoConcorrenciaIT extends AbstractMariaDbIntegrationTest {
    @Autowired PedidoRepository pedidos;
    @Autowired LojaRepository lojas;
    @Autowired UsuarioRepository usuarios;

    @Test
    void vinculoNaoPodeSerApagadoPorUmaCopiaAnteriorDoPedido() {
        Usuario cliente = new Usuario();
        cliente.setId("cli-vinculo"); cliente.setNome("Cliente"); cliente.setTelefone("11911111111");
        cliente.setPapel(Papel.CLIENTE); usuarios.save(cliente);
        Loja loja = new Loja();
        loja.setId("loja-vinculo"); loja.setNome("Loja"); loja.setAberto(true);
        loja.setEndereco(new EnderecoLoja("Rua A", "10", "Cidade", "SP", "01000-000", "Centro", null));
        DadosOperacionais dados = new DadosOperacionais();
        dados.setEntregaPropria(true); dados.setRetiradaNoLocal(false);
        dados.setTaxaEntregaBase(new BigDecimal("5.00"));
        dados.setTempoEntregaMin(20); dados.setTempoEntregaMax(40);
        loja.setDadosOperacionais(dados);
        loja.setFormasPagamento(new FormasPagamento());
        lojas.save(loja);
        Pedido pedido = new Pedido();
        pedido.setId("ped-vinculo"); pedido.setUsuarioId(cliente.getId()); pedido.setLoja(loja);
        pedido.setValorTotal(new BigDecimal("45.00")); pedido.setTaxaFrete(new BigDecimal("5.00"));
        pedido.setFormaPagamento("PIX"); pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setCriadoEm(Instant.now()); pedido.setPagamentoCriacaoIncerta(true);
        pedidos.saveAndFlush(pedido);
        Pedido anterior = pedidos.findById(pedido.getId()).orElseThrow();
        long versao = anterior.getVersion();
        assertEquals(1, pedidos.vincularAsaas(pedido.getId(), "pay-confirmado"));
        Pedido atual = pedidos.findById(pedido.getId()).orElseThrow();
        assertEquals(versao + 1, atual.getVersion());
        assertFalse(atual.isPagamentoCriacaoIncerta());
        assertEquals("pay-confirmado", atual.getAsaasPaymentId());
        assertEquals(0, pedidos.vincularAsaas(pedido.getId(), "pay-diferente"));
        anterior.setObservacao("Cópia desatualizada");
        assertThrows(OptimisticLockingFailureException.class, () -> pedidos.saveAndFlush(anterior));
        assertEquals("pay-confirmado", pedidos.findById(pedido.getId()).orElseThrow().getAsaasPaymentId());
    }
}
