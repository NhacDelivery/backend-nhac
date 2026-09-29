package br.com.nhac.backend_nhac.domain.financeiro;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StripePaymentService;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o fluxo de financeiro (GET /lojista/financeiro): cria um pedido de verdade via API,
 * confere que ele aparece no resumo/ranking/vendas-por-categoria/vendas-por-pagamento,
 * que um FUNCIONARIO enxerga os mesmos números que o dono (via LojaAccessService), e que
 * pedido CANCELADO não entra no faturamento nem no ranking.
 */
public class FinanceiroFlowIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LojaRepository lojaRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private TokenService tokenService;

    @MockitoBean
    private StripePaymentService stripePaymentService;

    private Usuario dono;
    private Usuario cliente;
    private Loja loja;
    private Produto produto;
    private String tokenDono;
    private String tokenCliente;

    @BeforeEach
    void prepararDados() {
        pedidoRepository.deleteAll();
        produtoRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = new Usuario();
        dono.setId(UUID.randomUUID().toString());
        dono.setNome("Dono Financeiro");
        dono.setEmail("dono.financeiro@teste.com");
        dono.setSenha("senha123");
        dono.setTelefone("11999990000");
        dono.setPapel(Papel.LOJISTA);
        usuarioRepository.save(dono);

        cliente = new Usuario();
        cliente.setId(UUID.randomUUID().toString());
        cliente.setNome("Cliente Financeiro");
        cliente.setEmail("cliente.financeiro@teste.com");
        cliente.setSenha("senha123");
        cliente.setTelefone("11988880000");
        cliente.setPapel(Papel.CLIENTE);
        usuarioRepository.save(cliente);

        loja = new Loja();
        loja.setId("loja-financeiro-teste");
        loja.setNome("Loja Teste Financeiro");
        loja.setUsuarioId(dono.getId());
        loja.setAberto(true);
        DadosOperacionais dadosOp = new DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        lojaRepository.save(loja);

        produto = new Produto();
        produto.setId(UUID.randomUUID().toString());
        produto.setNome("Pizza Financeiro");
        produto.setPreco(new BigDecimal("40.00"));
        produto.setAtivo(true);
        produto.setCategoriaMenu("Pizzas");
        produto.setLoja(loja);
        produtoRepository.save(produto);

        tokenDono = tokenService.gerarToken(dono);
        tokenCliente = tokenService.gerarToken(cliente);

        Mockito.when(stripePaymentService.criarPaymentIntentCartao(Mockito.any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedidoSalvo = invocation.getArgument(0);
            return new PedidoCriadoDTO(pedidoSalvo.getId(), "mock-secret", null, null);
        });
    }

    private void criarPedidoPago(int quantidade) throws Exception {
        PedidoCreateDTO.ItemPedidoDTO itemDto = new PedidoCreateDTO.ItemPedidoDTO(
                produto.getId(), produto.getNome(), null, quantidade
        );
        PedidoCreateDTO pedidoDto = new PedidoCreateDTO(
                loja.getId(),
                "DINHEIRO",
                null,
                null,
                null,
                new PedidoCreateDTO.EnderecoEntregaDTO("Rua Teste", "123", "Bairro", "Cidade", "SP", "00000-000", null),
                null,
                List.of(itemDto)
        );

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoDto)))
                .andExpect(status().isCreated());
    }

    @Test
    void deveRefletirPedidoNoResumoNoRankingENasVendas() throws Exception {
        criarPedidoPago(2); // 2 x 40.00 = 80.00

        mockMvc.perform(get("/api/v1/lojista/financeiro")
                        .header("Authorization", "Bearer " + tokenDono)
                        .param("periodo", "HOJE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("HOJE"))
                .andExpect(jsonPath("$.resumo.faturamentoPeriodo").value(80.00))
                .andExpect(jsonPath("$.resumo.numeroPedidos").value(1))
                .andExpect(jsonPath("$.resumo.ticketMedio").value(80.00))
                .andExpect(jsonPath("$.vendasPorCategoria[0].categoria").value("Pizzas"))
                .andExpect(jsonPath("$.vendasPorCategoria[0].valor").value(80.00))
                .andExpect(jsonPath("$.vendasPorFormaPagamento[0].formaPagamento").value("DINHEIRO"))
                .andExpect(jsonPath("$.produtosMaisVendidos[0].nome").value("Pizza Financeiro"))
                .andExpect(jsonPath("$.produtosMaisVendidos[0].quantidadeVendida").value(2))
                .andExpect(jsonPath("$.pedidosPorHora.length()").value(24))
                .andExpect(jsonPath("$.pedidosPorDiaSemana.length()").value(7));
    }

    @Test
    void funcionarioDeveVerOMesmoFinanceiroQueODono() throws Exception {
        criarPedidoPago(1); // 40.00

        Usuario funcionario = new Usuario();
        funcionario.setId(UUID.randomUUID().toString());
        funcionario.setNome("Funcionaria Financeiro");
        funcionario.setEmail("func.financeiro@teste.com");
        funcionario.setSenha("senha123");
        funcionario.setTelefone("11977770000");
        funcionario.setPapel(Papel.FUNCIONARIO);
        funcionario.setLojaVinculadaId(loja.getId());
        funcionario.setCargo("Gerente");
        usuarioRepository.save(funcionario);
        String tokenFuncionario = tokenService.gerarToken(funcionario);

        mockMvc.perform(get("/api/v1/lojista/financeiro")
                        .header("Authorization", "Bearer " + tokenFuncionario)
                        .param("periodo", "HOJE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.faturamentoPeriodo").value(40.00))
                .andExpect(jsonPath("$.resumo.numeroPedidos").value(1));
    }

    @Test
    void pedidoCanceladoNaoDeveEntrarNoFaturamentoMasDeveContarNaTaxaDeCancelamento() throws Exception {
        criarPedidoPago(1); // pedido válido: 40.00

        // Um cliente só pode manter um pedido ativo. Use outro cliente para
        // preservar o cenário de dois pedidos no mesmo financeiro da loja.
        Usuario outroCliente = new Usuario();
        outroCliente.setId(UUID.randomUUID().toString());
        outroCliente.setNome("Cliente Cancelamento");
        outroCliente.setEmail("cancelamento.financeiro@teste.com");
        outroCliente.setSenha("senha123");
        outroCliente.setTelefone("11966660000");
        outroCliente.setPapel(Papel.CLIENTE);
        usuarioRepository.save(outroCliente);
        String tokenOutroCliente = tokenService.gerarToken(outroCliente);

        // Cria o pedido do segundo cliente e cancela via PATCH /status.
        PedidoCreateDTO.ItemPedidoDTO itemDto = new PedidoCreateDTO.ItemPedidoDTO(produto.getId(), produto.getNome(), null, 1);
        PedidoCreateDTO pedidoDto = new PedidoCreateDTO(
                loja.getId(), "DINHEIRO", null, null, null,
                new PedidoCreateDTO.EnderecoEntregaDTO("Rua Teste", "123", "Bairro", "Cidade", "SP", "00000-000", null),
                null, List.of(itemDto)
        );
        String respostaPedido = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenOutroCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pedidoIdParaCancelar = objectMapper.readTree(respostaPedido).get("pedidoId").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/pedidos/" + pedidoIdParaCancelar + "/status")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lojista/financeiro")
                        .header("Authorization", "Bearer " + tokenDono)
                        .param("periodo", "HOJE"))
                .andExpect(status().isOk())
                // só o pedido válido conta como faturamento (o cancelado fica de fora)
                .andExpect(jsonPath("$.resumo.faturamentoPeriodo").value(40.00))
                .andExpect(jsonPath("$.resumo.numeroPedidos").value(1))
                // mas os 2 pedidos (1 válido + 1 cancelado) entram no denominador da taxa de cancelamento
                .andExpect(jsonPath("$.resumo.taxaCancelamentoPercentual").value(50.0));
    }

    @Test
    void deveRecusarAcessoSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/lojista/financeiro").param("periodo", "HOJE"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }
}
