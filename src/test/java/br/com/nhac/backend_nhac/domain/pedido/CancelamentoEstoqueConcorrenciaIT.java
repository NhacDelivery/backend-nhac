package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CancelamentoEstoqueConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired PedidoService pedidoService;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired LojaRepository lojaRepository;
    @Autowired UsuarioRepository usuarioRepository;

    @Test
    void pedidosDistintosDevolvemTodasAsUnidadesDoMesmoProduto() throws Exception {
        var cliente = new Usuario(); cliente.setId("cliente-distinto"); cliente.setNome("Cliente");
        usuarioRepository.save(cliente);
        var loja = loja(); lojaRepository.save(loja);
        var produto = produto(loja); produto.setEstoque(8); produtoRepository.save(produto);
        for (int i = 0; i < 2; i++) {
            var p = pedido(cliente, loja); p.setId("pedido-distinto-" + i);
            var item = new ItemPedido(); item.setId("item-distinto-" + i);
            item.setProduto(produto); item.setNome("Produto"); item.setPrecoHistorico(produto.getPreco());
            item.setQuantidade(i + 2); p.adicionarItem(item); pedidoRepository.saveAndFlush(p);
        }
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { inicio.await(); pedidoService.marcarComoCanceladoPorFalhaDePagamento("pedido-distinto-0"); return true; });
            var b = executor.submit(() -> { inicio.await(); pedidoService.marcarComoCanceladoPorFalhaDePagamento("pedido-distinto-1"); return true; });
            inicio.countDown();
            assertTrue(a.get(15, TimeUnit.SECONDS)); assertTrue(b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(13, produtoRepository.findById(produto.getId()).orElseThrow().getEstoque());
    }

    @Test
    void cancelamentosConcorrentesNaoDevemDevolverEstoqueDuasVezes() throws Exception {
        Usuario cliente = new Usuario();
        cliente.setId("cli-cancel");
        cliente.setNome("Cliente");
        cliente.setTelefone("11911111111");
        cliente.setPapel(Papel.CLIENTE);
        usuarioRepository.save(cliente);

        Loja loja = loja();
        lojaRepository.save(loja);

        Produto produto = produto(loja);
        produto.setEstoque(8); // já representa 2 unidades reservadas de um estoque original 10
        produtoRepository.save(produto);

        Pedido pedido = pedido(cliente, loja);
        ItemPedido item = new ItemPedido();
        item.setId("item-cancel");
        item.setProduto(produto);
        item.setNome(produto.getNome());
        item.setPrecoHistorico(produto.getPreco());
        item.setQuantidade(2);
        pedido.adicionarItem(item);
        pedidoRepository.saveAndFlush(pedido);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Callable<Boolean> cancelar = () -> {
                largada.await();
                try {
                    pedidoService.marcarComoCanceladoPorFalhaDePagamento("ped-cancel");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            };

            Future<Boolean> f1 = executor.submit(cancelar);
            Future<Boolean> f2 = executor.submit(cancelar);
            largada.countDown();
            f1.get();
            f2.get();
        } finally {
            executor.shutdownNow();
        }

        Pedido finalPedido = pedidoRepository.findById("ped-cancel").orElseThrow();
        Produto finalProduto = produtoRepository.findById("prod-cancel").orElseThrow();

        assertEquals(StatusPedido.CANCELADO, finalPedido.getStatus());
        assertEquals(10, finalProduto.getEstoque());
    }

    private Loja loja() {
        Loja loja = new Loja();
        loja.setId("loja-cancel");
        loja.setNome("Loja");
        loja.setAberto(true);
        DadosOperacionais dados = new DadosOperacionais();
        dados.setEntregaPropria(true);
        dados.setRetiradaNoLocal(false);
        dados.setTaxaEntregaBase(new BigDecimal("5.00"));
        dados.setTempoEntregaMin(20);
        dados.setTempoEntregaMax(40);
        loja.setDadosOperacionais(dados);
        loja.setEndereco(new EnderecoLoja("Rua A", "10", "Cidade", "SP", "01000-000", "Centro", null));
        return loja;
    }

    private Produto produto(Loja loja) {
        Produto p = new Produto();
        p.setId("prod-cancel");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("20.00"));
        p.setCategoriaMenu("Teste");
        p.setAtivo(true);
        p.setLoja(loja);
        return p;
    }

    private Pedido pedido(Usuario cliente, Loja loja) {
        Pedido p = new Pedido();
        p.setId("ped-cancel");
        p.setUsuarioId(cliente.getId());
        p.setLoja(loja);
        p.setValorTotal(new BigDecimal("45.00"));
        p.setTaxaFrete(new BigDecimal("5.00"));
        p.setFormaPagamento("PIX");
        p.setStatus(StatusPedido.PENDENTE);
        p.setCriadoEm(Instant.now());
        return p;
    }
}
