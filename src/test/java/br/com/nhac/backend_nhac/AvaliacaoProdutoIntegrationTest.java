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
    @Autowired PedidoReservaService reserva;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private Usuario comprador, outro;
    private Produto produto, segundo;
    @BeforeEach void dados() {
        comprador=usuarios.save(Usuario.builder().id("comprador").nome("Comprador").email("comprador@review.test").telefone("11911111111").build());
        outro=usuarios.save(Usuario.builder().id("outro").nome("Outro").email("outro@review.test").telefone("11922222222").build());
        var loja=lojas.save(Loja.builder().id("loja-review").nome("Loja").isAberto(true).dadosOperacionais(new DadosOperacionais()).endereco(new EnderecoLoja("Rua", "1", "Osasco", "SP", "06000-000", "Centro", null)).build());
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
    @Autowired UsuarioService usuarioService;
    @Test void dispositivoDePushNaoPermaneceVinculadoAOutraConta() {
        usuarioService.atualizarUsuarioParcial(comprador.getId(), new br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioAtualizarDTO(null,null,null,null,"dispositivo",null));
        usuarioService.atualizarUsuarioParcial(outro.getId(), new br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioAtualizarDTO(null,null,null,null,"dispositivo",null));
        assertNull(usuarios.findById(comprador.getId()).orElseThrow().getFcmToken());
        assertEquals("dispositivo", usuarios.findById(outro.getId()).orElseThrow().getFcmToken());
    }
    @Test void categoriasIgnoramEstoqueZeroEProdutosInativos() throws Exception {
        produto.setEstoque(3); produtos.save(produto);
        segundo.setCategoriaMenu("Inexistente"); segundo.setEstoque(0); produtos.save(segundo);
        mockMvc.perform(get("/api/v1/produtos/categorias")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("Lanches")).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void consultaAvaliacaoExistenteExigeDonoDoPedido() throws Exception {
        pedido("consulta",produto,StatusPedido.ENTREGUE,1);
        service.criar(produto.getId(),comprador,new AvaliacaoProdutoDTO("consulta",5,"Ótimo",List.of()));
        mockMvc.perform(get("/api/v1/pedidos/consulta/avaliacoes-produtos").with(user(comprador)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].produtoId").value(produto.getId()));
        mockMvc.perform(get("/api/v1/pedidos/consulta/avaliacoes-produtos").with(user(outro))).andExpect(status().isForbidden());
    }
    @Test void adicionaisObrigatoriosPrecoServidorESnapshot() {
        produto.setEstoque(5);
        var grupo=new GrupoAdicional(); grupo.setId("grupo-1"); grupo.setNome("Molhos"); grupo.setProduto(produto);
        grupo.setObrigatorio(true); grupo.setMinimo(1); grupo.setMaximo(1);
        var extra=new ItemAdicional(); extra.setId("extra-1"); extra.setNome("Barbecue"); extra.setPreco(new BigDecimal("2.50")); extra.setGrupoAdicional(grupo);
        grupo.getItens().add(extra); produto.getAdicionais().add(grupo); produtos.save(produto);
        var endereco=new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO.EnderecoEntregaDTO("Rua","1","Centro","Osasco","SP","06000-000",null,-23.0,-46.0);
        java.util.function.Function<List<String>,br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO> dto = ids -> new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO(
            "loja-review","DINHEIRO",null,null,null,endereco,null,List.of(new br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO.ItemPedidoDTO(produto.getId(),"Cliente",null,2,ids)));
        assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,()->reserva.reservar(dto.apply(List.of()),comprador,"sem-adicional"));
        assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,()->reserva.reservar(dto.apply(List.of("extra-estranho")),comprador,"extra-errado"));
        var pedido=reserva.reservar(dto.apply(List.of("extra-1")),comprador,"com-adicional");
        assertEquals(new BigDecimal("12.50"),jdbc.queryForObject("select preco_historico from tb_itens_pedido where pedido_id=?",BigDecimal.class,pedido.pedido().getId()));
        assertTrue(jdbc.queryForObject("select descricao from tb_item_pedido_adicionais",String.class).contains("Barbecue"));
        assertEquals(pedido.pedido().getId(),reserva.reservar(dto.apply(List.of("extra-1")),comprador,"com-adicional").pedido().getId());
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
