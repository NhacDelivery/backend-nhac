package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.entregador.*;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"nhac.email.mock-mode=true", "nhac.google.enabled=false"})
class CodigoEntregaFlowIT extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired LojaRepository lojas;
    @Autowired EntregadorRepository entregadores;
    @Autowired PedidoRepository pedidos;
    @Autowired TokenService tokens;
    @Autowired br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorRepository avaliacoes;
    @Autowired br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorService avaliacaoService;
    @Autowired org.springframework.cache.CacheManager caches;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private String authorization;

    @BeforeEach
    void fixture() {
        Usuario cliente = usuario("cliente-codigo");
        Usuario motoboy = usuario("motoboy-codigo");
        Loja loja = new Loja();
        loja.setId("loja-codigo");
        loja.setNome("Loja código");
        loja.setAberto(true);
        DadosOperacionais operacao = new DadosOperacionais();
        operacao.setTaxaEntregaBase(new BigDecimal("5.00"));
        loja.setDadosOperacionais(operacao);
        loja.setEndereco(new EnderecoLoja("Rua A", "1", "São Paulo", "SP", "01001-000", "Sé", null));
        lojas.saveAndFlush(loja);
        Entregador e = Entregador.builder().id("entregador-codigo").usuario(motoboy)
                .cnh("12345678901").placaVeiculo("ITF1A23").tipoVeiculo(TipoVeiculo.MOTO)
                .statusOperacional(StatusOperacional.EM_ENTREGA).ativo(true).build();
        entregadores.saveAndFlush(e);
        Pedido p = new Pedido();
        p.setId("pedido-codigo");
        p.setUsuarioId(cliente.getId());
        p.setLoja(loja);
        p.setEntregador(e);
        p.setStatus(StatusPedido.SAIU_ENTREGA);
        p.setValorTotal(new BigDecimal("30.00"));
        p.setTaxaFrete(new BigDecimal("5.00"));
        p.setFormaPagamento("DINHEIRO");
        p.setCriadoEm(Instant.now());
        p.setColetadoEm(Instant.now());
        p.setCodigoEntrega("0123");
        pedidos.saveAndFlush(p);
        authorization = "Bearer " + tokens.gerarToken(motoboy);
    }

    @Test
    void codigoErradoPersisteEAcertoComZeroInicialConcluiEReseta() throws Exception {
        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                    .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"codigo\":\"9999\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("CODIGO_ENTREGA_INVALIDO"))
                    .andExpect(jsonPath("$.details.tentativasRestantes").value(5 - tentativa));
            Pedido atual = pedidos.findById("pedido-codigo").orElseThrow();
            assertEquals(tentativa, atual.getCodigoEntregaTentativas());
            assertEquals(StatusPedido.SAIU_ENTREGA, atual.getStatus());
            assertNull(atual.getEntregueEm());
        }
        concluirCorretamente();
        Pedido atual = pedidos.findById("pedido-codigo").orElseThrow();
        assertEquals(StatusPedido.ENTREGUE, atual.getStatus());
        assertEquals(0, atual.getCodigoEntregaTentativas());
        assertNull(atual.getCodigoEntregaBloqueadoAte());
        assertNotNull(atual.getEntregueEm());
        assertEquals(StatusOperacional.ONLINE,
                entregadores.findById("entregador-codigo").orElseThrow().getStatusOperacional());
        Instant entregueEm = atual.getEntregueEm();
        concluirCorretamente();
        assertEquals(entregueEm, pedidos.findById("pedido-codigo").orElseThrow().getEntregueEm());
    }

    @Test
    void quintaFalhaBloqueiaECodigoCorretoNaoContornaBloqueio() throws Exception {
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                    .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"codigo\":\"9999\"}"))
                    .andExpect(status().is(tentativa < 5 ? 400 : 429));
        }
        Pedido atual = pedidos.findById("pedido-codigo").orElseThrow();
        assertEquals(5, atual.getCodigoEntregaTentativas());
        assertTrue(atual.getCodigoEntregaBloqueadoAte().isAfter(Instant.now()));
        mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"0123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("CODIGO_ENTREGA_BLOQUEADO"))
                .andExpect(jsonPath("$.details.tentativaLiberadaEm").exists());
        atual = pedidos.findById("pedido-codigo").orElseThrow();
        assertEquals(5, atual.getCodigoEntregaTentativas());
        assertEquals(StatusPedido.SAIU_ENTREGA, atual.getStatus());
        assertNull(atual.getEntregueEm());
    }

    @Test
    void codigoAusenteNaoConsomeTentativaNemConclui() throws Exception {
        mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        Pedido atual = pedidos.findById("pedido-codigo").orElseThrow();
        assertEquals(0, atual.getCodigoEntregaTentativas());
        assertEquals(StatusPedido.SAIU_ENTREGA, atual.getStatus());
    }

    @Test
    void cincoFalhasSimultaneasBloqueiamSemPerderTentativas() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(5)) {
            var inicio = new java.util.concurrent.CyclicBarrier(5);
            var respostas = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 5; i++) {
                respostas.add(executor.submit(() -> {
                    inicio.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                            .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"codigo\":\"9999\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            var status = new java.util.ArrayList<Integer>();
            for (var resposta : respostas) status.add(resposta.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(4, status.stream().filter(s -> s == 400).count());
            assertEquals(1, status.stream().filter(s -> s == 429).count());
        }
        Pedido atual = pedidos.findById("pedido-codigo").orElseThrow();
        assertEquals(5, atual.getCodigoEntregaTentativas());
        assertNotNull(atual.getCodigoEntregaBloqueadoAte());
        mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"0123\"}"))
                .andExpect(status().isTooManyRequests());
        assertEquals(StatusPedido.SAIU_ENTREGA, pedidos.findById("pedido-codigo").orElseThrow().getStatus());
    }

    @Test
    void agregacaoExcluiFimDoPeriodoEPreservaFretesSemCarregarPedidos() {
        Pedido p = pedidos.findById("pedido-codigo").orElseThrow();
        p.setStatus(StatusPedido.ENTREGUE);
        p.setEntregueEm(Instant.parse("2026-10-04T03:00:00Z"));
        p = pedidos.saveAndFlush(p);
        Instant inicio = Instant.parse("2026-10-04T03:00:00Z");
        Instant fim = Instant.parse("2026-10-05T03:00:00Z");
        var linhas = pedidos.somarFretesPorHora("entregador-codigo", StatusPedido.ENTREGUE, inicio, fim);
        assertEquals(1, linhas.size());
        assertEquals(0, new BigDecimal("5.00").compareTo(linhas.getFirst().valor()));
        assertEquals(1, linhas.getFirst().entregas());
        assertEquals(inicio, linhas.getFirst().referencia());
        p.setEntregueEm(fim);
        p = pedidos.saveAndFlush(p);
        assertTrue(pedidos.somarFretesPorHora("entregador-codigo", StatusPedido.ENTREGUE, inicio, fim).isEmpty());
        p.setEntregueEm(null);
        p.setCriadoEm(inicio);
        p = pedidos.saveAndFlush(p);
        assertEquals(1, pedidos.somarFretesPorHora("entregador-codigo", StatusPedido.ENTREGUE, inicio, fim).size());
    }

    @Test
    void resumoEmCacheEInvalidadoAposAvaliacaoConfirmada() {
        var cache = caches.getCache("entregadorAvaliacoes");
        cache.clear();
        assertEquals(0, avaliacoes.resumir("entregador-codigo").total());
        assertNotNull(cache.get("entregador-codigo"));
        Pedido p = pedidos.findById("pedido-codigo").orElseThrow();
        p.setStatus(StatusPedido.ENTREGUE);
        pedidos.saveAndFlush(p);
        avaliacaoService.criar(p.getId(),
                new br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO(5, "Pontual"),
                usuarios.findById("cliente-codigo").orElseThrow());
        assertNull(cache.get("entregador-codigo"));
        var resumo = avaliacoes.resumir("entregador-codigo");
        assertEquals(1, resumo.total());
        assertEquals(5.0, resumo.media());
    }

    @Test
    void estadoConsolidadoEAutenticadoEHistoricoNaoExpoeEnderecoCompleto() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/entregador/estado")
                .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.perfil.id").value("entregador-codigo"))
                .andExpect(jsonPath("$.entrega.pedidoId").value("pedido-codigo"))
                .andExpect(jsonPath("$.ofertas").isEmpty());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/entregador/estado"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/entregador/entregas")
                .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].enderecoEntrega").doesNotExist());
    }

    @Test
    void rollbackDaAvaliacaoPreservaResumoEmCache() {
        var cache = caches.getCache("entregadorAvaliacoes");
        cache.clear();
        avaliacoes.resumir("entregador-codigo");
        var anterior = cache.get("entregador-codigo").get();
        Pedido p = pedidos.findById("pedido-codigo").orElseThrow();
        p.setStatus(StatusPedido.ENTREGUE);
        pedidos.saveAndFlush(p);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            avaliacaoService.criar("pedido-codigo",
                    new br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO(5, null),
                    usuarios.findById("cliente-codigo").orElseThrow());
            throw new IllegalStateException("Rollback simulado");
        }));
        assertEquals(anterior, cache.get("entregador-codigo").get());
        assertEquals(0, avaliacoes.countByEntregadorId("entregador-codigo"));
    }

    private void concluirCorretamente() throws Exception {
        mockMvc.perform(post("/api/v1/entregas/pedido-codigo/concluir")
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"0123\"}"))
                .andExpect(status().isNoContent());
    }

    private Usuario usuario(String id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(id);
        u.setEmail(id + "@nhac.local");
        u.setTelefone("+5511999990001");
        u.setSenha("senha-fixture");
        u.setPapel(Papel.CLIENTE);
        u.setAtivo(true);
        u.setEmailVerificado(true);
        u.setTelefoneVerificado(true);
        u.setEnderecos(new ArrayList<>());
        return usuarios.saveAndFlush(u);
    }
}
