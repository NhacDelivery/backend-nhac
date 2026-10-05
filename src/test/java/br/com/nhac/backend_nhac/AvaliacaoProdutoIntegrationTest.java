package br.com.nhac.backend_nhac;
import br.com.nhac.backend_nhac.domain.avaliacao_produto.*;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AvaliacaoProdutoIntegrationTest extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired LojaRepository lojas;
    @Autowired ProdutoRepository produtos;
    @Autowired PedidoRepository pedidos;
    @Autowired AvaliacaoProdutoService service;
    private Usuario comprador, outro;
    private Produto produto, segundo;
    @BeforeEach void dados() {
        comprador=usuarios.save(Usuario.builder().id("comprador").nome("Comprador").email("comprador@review.test").telefone("11911111111").build());
        outro=usuarios.save(Usuario.builder().id("outro").nome("Outro").email("outro@review.test").telefone("11922222222").build());
        var loja=lojas.save(Loja.builder().id("loja-review").nome("Loja").isAberto(true).dadosOperacionais(new DadosOperacionais()).build());
        produto=produto("produto-a",loja,10); segundo=produto("produto-b",loja,0);
    }
    private Produto produto(String id,Loja loja,int desconto) {
        var p=new Produto(); p.setId(id); p.setLoja(loja); p.setNome(id); p.setPreco(BigDecimal.TEN); p.setCategoriaMenu("Lanches"); p.setAtivo(true); p.setPercentualDesconto(desconto);
        return produtos.save(p);
    }
    private String pedido(String id,Produto produto,StatusPedido status,int quantidade) {
        var p=new Pedido(); p.setId(id); p.setUsuarioId(comprador.getId()); p.setLoja(produto.getLoja()); p.setValorTotal(BigDecimal.TEN);
        p.setTaxaFrete(BigDecimal.ZERO); p.setFormaPagamento("DINHEIRO"); p.setStatus(status); p.setCriadoEm(Instant.now());
        var i=new ItemPedido(); i.setId(UUID.randomUUID().toString()); i.setProduto(produto); i.setNome(produto.getNome()); i.setPrecoHistorico(BigDecimal.TEN); i.setQuantidade(quantidade); p.adicionarItem(i);
        pedidos.save(p); return id;
    }
    @Test void avaliacaoDeProdutoValidaCompraEFiltrosEReplay() throws Exception {
        String id=pedido("entregue",produto,StatusPedido.ENTREGUE,1);
        var dto=new AvaliacaoProdutoDTO(id,5,"Muito bom",List.of("https://example.com/foto.jpg"));
        var primeira=service.criar(produto.getId(),comprador,dto);
        assertEquals(primeira.id(),service.criar(produto.getId(),comprador,dto).id());
        mockMvc.perform(get("/api/v1/produtos/"+produto.getId()+"/avaliacoes").param("fotos","true").param("positivas","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].imagens[0]").value("https://example.com/foto.jpg"));
        mockMvc.perform(get("/api/v1/produtos/"+segundo.getId()+"/avaliacoes"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/produtos/"+produto.getId()+"/avaliacoes/resumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalAvaliacoes").value(1)).andExpect(jsonPath("$.mediaNotas").value(5.0));
        mockMvc.perform(post("/api/v1/produtos/"+produto.getId()+"/avaliacoes").with(user(outro)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":\"entregue\",\"nota\":5}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/produtos/"+segundo.getId()+"/avaliacoes").with(user(comprador)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":\"entregue\",\"nota\":5}")).andExpect(status().isBadRequest());
    }
    @Test void impedeAvaliarPedidoNaoEntregue() throws Exception {
        pedido("pendente",produto,StatusPedido.PENDENTE,1);
        mockMvc.perform(post("/api/v1/produtos/"+produto.getId()+"/avaliacoes").with(user(comprador)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":\"pendente\",\"nota\":5}")).andExpect(status().isBadRequest());
    }
    @Test void catalogoDistingueDescontoEVendasEntregues() throws Exception {
        pedido("entregue",segundo,StatusPedido.ENTREGUE,2);
        pedido("cancelado",produto,StatusPedido.CANCELADO,100);
        mockMvc.perform(get("/api/v1/lojas/loja-review/catalogo").param("filtro","Vendidos"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(segundo.getId()));
        mockMvc.perform(get("/api/v1/lojas/loja-review/catalogo").param("filtro","Em destaque"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(produto.getId()));
        mockMvc.perform(get("/api/v1/lojas/loja-review/avaliacoes/resumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
}
