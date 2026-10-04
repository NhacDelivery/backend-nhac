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
