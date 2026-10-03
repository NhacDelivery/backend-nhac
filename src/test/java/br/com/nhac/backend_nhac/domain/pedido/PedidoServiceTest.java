package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.loja.FreteService;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StripePaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.PedidoAtivoException;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private StripePaymentService stripePaymentService;
    @Mock private AsaasPaymentService asaasPaymentService;
    @Mock private LojaAccessService lojaAccessService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntregadorRepository entregadorRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private Environment environment;

    @Spy
    private FreteService freteService = new FreteService();

    @InjectMocks private PedidoService pedidoService;

    @BeforeEach
    void configurarAcessoDaLoja() {
        org.springframework.test.util.ReflectionTestUtils.setField(pedidoService, "reservaService",
                new PedidoReservaService(pedidoRepository, lojaRepository, produtoRepository, usuarioRepository, null));
        lenient().when(usuarioRepository.findLockedById(anyString()))
                .thenAnswer(invocation -> {
                    Usuario usuario = usuarioPadrao();
                    usuario.setId(invocation.getArgument(0));
                    return Optional.of(usuario);
                });
        lenient().when(lojaAccessService.temAcessoALoja(any(Usuario.class), anyString()))
                .thenAnswer(invocation -> {
                    Usuario usuario = invocation.getArgument(0);
                    return "user_001".equals(usuario.getId()) || "dono_loja".equals(usuario.getId());
                });
    }

    private Usuario usuarioPadrao() {
        Usuario usuario = new Usuario();
        usuario.setId("user_teste_123");
        usuario.setNome("Teste");
        usuario.setEmail("teste@nhac.com");
        usuario.setTelefone("11999999999");
        return usuario;
    }

    @Test
    void pedidoAtivoNaoImpedeValidacaoDaNovaCompra() {
        Pedido ativo = new Pedido();
        ativo.setId("pedido-em-andamento");
        ativo.setStatus(StatusPedido.PAGO);
        lenient().when(pedidoRepository.findFirstByUsuarioIdAndStatusInOrderByCriadoEmDesc(
                eq("user_teste_123"), anyList())).thenReturn(Optional.of(ativo));

        var endereco = new PedidoCreateDTO.EnderecoEntregaDTO("Rua", "1", "Bairro", "Cidade", "SP", "01000-000", null, -23.5, -46.7);
        var dto = new PedidoCreateDTO("loja-fechada", "DINHEIRO", null, null, null, endereco, null,
                List.of(new PedidoCreateDTO.ItemPedidoDTO("produto", "Produto", null, 1)));
        assertThrows(br.com.nhac.backend_nhac.exceptions.LojaFechadaException.class,
                () -> pedidoService.finalizarPedido(dto, usuarioPadrao(), "nova-compra"));
        verify(pedidoRepository, never()).findFirstByUsuarioIdAndStatusInOrderByCriadoEmDesc(anyString(), anyList());
    }

    @Test
    void simulaPixSomenteNoSandboxEEncerraCobrancaAntesDeMarcarPago() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("asaas.api.url", ""))
                .thenReturn("https://sandbox.asaas.com/api/v3");
        Pedido pedido = new Pedido();
        pedido.setId("pedido-pix");
        pedido.setUsuarioId("user_teste_123");
        pedido.setFormaPagamento("PIX");
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setPagamentoExpiraEm(Instant.now().plusSeconds(120));
        pedido.setAsaasPaymentId("pay-sandbox");
        when(pedidoRepository.findLockedById("pedido-pix")).thenReturn(Optional.of(pedido));
        when(asaasPaymentService.consultarStatus("pay-sandbox")).thenReturn("PENDING");

        pedidoService.simularPagamento("pedido-pix", "user_teste_123");

        assertEquals(StatusPedido.PAGO, pedido.getStatus());
        var ordem = inOrder(asaasPaymentService, pedidoRepository);
        ordem.verify(asaasPaymentService).cancelarCobranca("pay-sandbox");
        ordem.verify(pedidoRepository).save(pedido);
    }

    @Test
    void naoSimulaPixComPerfilDeProducao() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        assertThrows(AcessoNegadoException.class,
                () -> pedidoService.simularPagamento("pedido-pix", "user_teste_123"));
        verifyNoInteractions(asaasPaymentService, pedidoRepository);
    }

    @Test
    @DisplayName("Deve calcular o preço total usando o valor do Banco de Dados, prevenindo fraudes do Frontend")
    void deveCalcularPrecoRealDoBancoIgnorandoOCliente() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );

        Loja lojaMock = new Loja();
        lojaMock.setId("loja_1");
        lojaMock.setAberto(true);

        Produto burgerMock = new Produto();
        burgerMock.setId("prod_1");
        burgerMock.setLoja(lojaMock);
        burgerMock.setPreco(new BigDecimal("45.00"));
        burgerMock.setAtivo(true);
        burgerMock.setEstoque(100);

        PedidoCreateDTO.ItemPedidoDTO itemFraudulento = new PedidoCreateDTO.ItemPedidoDTO(
                "prod_1", "Hambúrguer", "http://imagem.com/burger.jpg", 2
        );

        PedidoCreateDTO dto = new PedidoCreateDTO(
                "loja_1",
                "DINHEIRO",
                "Sem cebola",
                null,
                null,
                enderecoMock,
                null,
                List.of(itemFraudulento)
        );

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(lojaMock));
        when(produtoRepository.findById("prod_1")).thenReturn(Optional.of(burgerMock));
        when(produtoRepository.decrementarEstoqueSeDisponivel("prod_1", 2)).thenReturn(1);

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido p = invocation.getArgument(0);
            p.setId("pedido_gerado_001");
            return p;
        });

        pedidoService.finalizarPedido(dto, usuario, null);

        verify(pedidoRepository).save(argThat(pedido -> {
            boolean totalCorreto = pedido.getValorTotal().compareTo(new BigDecimal("95.00")) == 0;
            boolean donoCorreto = pedido.getUsuarioId().equals(usuario.getId());

            boolean enderecoNaoNulo = pedido.getEnderecoEntrega() != null;

            return totalCorreto && donoCorreto && enderecoNaoNulo;
        }));
    }

    @Test
    @DisplayName("Deve lançar LojaFechadaException quando a loja não existir ou estiver fechada")
    void deveLancarExcecaoQuandoLojaNaoExisteOuEstaFechada() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );
        PedidoCreateDTO.ItemPedidoDTO item = new PedidoCreateDTO.ItemPedidoDTO(
                "prod_1", "Hambúrguer", "http://imagem.com/burger.jpg", 1
        );
        PedidoCreateDTO dto = new PedidoCreateDTO("loja_fechada", "DINHEIRO", null, null, null, enderecoMock, null, List.of(item));

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_fechada")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(br.com.nhac.backend_nhac.exceptions.LojaFechadaException.class,
                () -> pedidoService.finalizarPedido(dto, usuario, null));

        assertEquals("A loja informada está fechada no momento.", excecao.getMessage());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Deve lançar ProdutoNaoEncontradoException quando um produto do carrinho não existir")
    void deveLancarExcecaoQuandoProdutoNaoExiste() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );

        Loja lojaMock = new Loja();
        lojaMock.setId("loja_1");
        lojaMock.setAberto(true);

        PedidoCreateDTO.ItemPedidoDTO itemFantasma = new PedidoCreateDTO.ItemPedidoDTO(
                "prod_fantasma", "Produto Inexistente", null, 1
        );
        PedidoCreateDTO dto = new PedidoCreateDTO("loja_1", "DINHEIRO", null, null, null, enderecoMock, null, List.of(itemFantasma));

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(lojaMock));
        when(produtoRepository.findById("prod_fantasma")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(br.com.nhac.backend_nhac.exceptions.ProdutoNaoEncontradoException.class,
                () -> pedidoService.finalizarPedido(dto, usuario, null));

        assertEquals("Produto com ID 'prod_fantasma' não encontrado na loja 'loja_1'.", excecao.getMessage());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException quando o produto pertencer a outra loja")
    void deveLancarExcecaoQuandoProdutoPertenceAOutraLoja() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );

        Loja lojaSelecionada = new Loja();
        lojaSelecionada.setId("loja_1");
        lojaSelecionada.setAberto(true);

        Loja outraLoja = new Loja();
        outraLoja.setId("loja_2");

        Produto produtoDeOutraLoja = new Produto();
        produtoDeOutraLoja.setId("prod_1");
        produtoDeOutraLoja.setNome("Hambúrguer");
        produtoDeOutraLoja.setLoja(outraLoja);
        produtoDeOutraLoja.setPreco(new BigDecimal("30.00"));
        produtoDeOutraLoja.setAtivo(true);
        produtoDeOutraLoja.setEstoque(10);

        PedidoCreateDTO.ItemPedidoDTO item = new PedidoCreateDTO.ItemPedidoDTO(
                "prod_1", "Hambúrguer", null, 1
        );
        PedidoCreateDTO dto = new PedidoCreateDTO("loja_1", "DINHEIRO", null, null, null, enderecoMock, null, List.of(item));

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(lojaSelecionada));
        when(produtoRepository.findById("prod_1")).thenReturn(Optional.of(produtoDeOutraLoja));

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> pedidoService.finalizarPedido(dto, usuario, null));

        assertEquals("O produto 'Hambúrguer' não pertence à loja selecionada.", excecao.getMessage());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Deve somar corretamente o valor de múltiplos itens e aplicar a taxa fixa de frete")
    void deveSomarValoresDeMultiplosItensComTaxaDeFreteFixa() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );

        Loja lojaMock = new Loja();
        lojaMock.setId("loja_1");
        lojaMock.setAberto(true);

        Produto produto1 = new Produto();
        produto1.setId("prod_1");
        produto1.setLoja(lojaMock);
        produto1.setPreco(new BigDecimal("10.00"));
        produto1.setAtivo(true);
        produto1.setEstoque(10);

        Produto produto2 = new Produto();
        produto2.setId("prod_2");
        produto2.setLoja(lojaMock);
        produto2.setPreco(new BigDecimal("20.00"));
        produto2.setAtivo(true);
        produto2.setEstoque(10);

        PedidoCreateDTO.ItemPedidoDTO item1 = new PedidoCreateDTO.ItemPedidoDTO("prod_1", "Item 1", null, 2);
        PedidoCreateDTO.ItemPedidoDTO item2 = new PedidoCreateDTO.ItemPedidoDTO("prod_2", "Item 2", null, 1);

        PedidoCreateDTO dto = new PedidoCreateDTO("loja_1", "DINHEIRO", null, null, null, enderecoMock, null, List.of(item1, item2));

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(lojaMock));
        when(produtoRepository.findById("prod_1")).thenReturn(Optional.of(produto1));
        when(produtoRepository.findById("prod_2")).thenReturn(Optional.of(produto2));
        when(produtoRepository.decrementarEstoqueSeDisponivel("prod_1", 2)).thenReturn(1);
        when(produtoRepository.decrementarEstoqueSeDisponivel("prod_2", 1)).thenReturn(1);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido p = invocation.getArgument(0);
            p.setId("pedido_gerado_002");
            return p;
        });

        pedidoService.finalizarPedido(dto, usuario, null);

        // (10.00 * 2) + (20.00 * 1) = 40.00 + taxa fixa de 5.00 = 45.00
        verify(pedidoRepository).save(argThat(pedido ->
                pedido.getValorTotal().compareTo(new BigDecimal("45.00")) == 0
                        && pedido.getTaxaFrete().compareTo(new BigDecimal("5.00")) == 0
                        && pedido.getItens().size() == 2
        ));
    }

    @Test
    @DisplayName("Deve usar a taxa de entrega própria da loja quando configurada, em vez do valor fixo")
    void deveUsarTaxaDeEntregaDaLojaQuandoConfigurada() {
        Usuario usuario = usuarioPadrao();

        PedidoCreateDTO.EnderecoEntregaDTO enderecoMock = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "01000-000", null
        );

        Loja lojaMock = new Loja();
        lojaMock.setId("loja_1");
        lojaMock.setAberto(true);
        DadosOperacionais dados = new DadosOperacionais();
        dados.setTaxaEntregaBase(new BigDecimal("7.50"));
        lojaMock.setDadosOperacionais(dados);

        Produto produto = new Produto();
        produto.setId("prod_1");
        produto.setLoja(lojaMock);
        produto.setPreco(new BigDecimal("10.00"));
        produto.setAtivo(true);
        produto.setEstoque(10);

        PedidoCreateDTO.ItemPedidoDTO item = new PedidoCreateDTO.ItemPedidoDTO("prod_1", "Item 1", null, 1);
        PedidoCreateDTO dto = new PedidoCreateDTO("loja_1", "DINHEIRO", null, null, null, enderecoMock, null, List.of(item));

        when(lojaRepository.findByIdAndIsAbertoTrue("loja_1")).thenReturn(Optional.of(lojaMock));
        when(produtoRepository.findById("prod_1")).thenReturn(Optional.of(produto));
        when(produtoRepository.decrementarEstoqueSeDisponivel("prod_1", 1)).thenReturn(1);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pedidoService.finalizarPedido(dto, usuario, null);

        verify(pedidoRepository).save(argThat(pedido ->
                pedido.getTaxaFrete().compareTo(new BigDecimal("7.50")) == 0
                        && pedido.getValorTotal().compareTo(new BigDecimal("17.50")) == 0
        ));
    }

    @Test
    @DisplayName("Deve buscar pedido com sucesso quando existir e o usuário for dono")
    void deveBuscarPedidoQuandoExistirEUsuarioForDono() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setNome("Loja Teste");

        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_123");
        pedidoMock.setLoja(lojaMock);
        pedidoMock.setValorTotal(new BigDecimal("100.00"));
        pedidoMock.setTaxaFrete(new BigDecimal("5.00"));

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        var dto = pedidoService.buscarPedido("pedido_123", "user_123");

        assertNotNull(dto);
        assertEquals("pedido_123", dto.id());
        assertEquals("loja_001", dto.lojaId());
        assertEquals("user_123", dto.usuarioId());
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException quando usuário não for dono do pedido")
    void deveLancarAcessoNegadoQuandoUsuarioNaoForDono() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_dono");

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        Exception excecao = assertThrows(br.com.nhac.backend_nhac.exceptions.AcessoNegadoException.class,
                () -> pedidoService.buscarPedido("pedido_123", "user_intruso"));

        assertEquals("Acesso negado: você não tem permissão para visualizar este pedido.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve lançar IdNaoEncontradoException quando pedido não existir")
    void deveLancarIdNaoEncontradoQuandoPedidoNaoExistir() {
        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.empty());

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> pedidoService.buscarPedido("pedido_123", "user_123"));

        assertEquals("Pedido não encontrado.", excecao.getMessage());
    }

    @Test
    @DisplayName("Deve retornar Page de PedidoResumoDTO quando listar pedidos do usuário")
    void deveRetornarPageDePedidoResumoDTOQuandoListarPedidosDoUsuario() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_123");
        pedidoMock.setValorTotal(new BigDecimal("100.00"));

        Pageable pageable = PageRequest.of(0, 10);
        Page<Pedido> pageMock = new PageImpl<>(List.of(pedidoMock));

        when(pedidoRepository.findByUsuarioId("user_123", pageable)).thenReturn(pageMock);

        var resultado = pedidoService.listarMeusPedidos("user_123", pageable);

        assertNotNull(resultado);
        assertEquals(1, resultado.getContent().size());
        assertEquals("pedido_123", resultado.getContent().get(0).id());
    }

    @Test
    @DisplayName("Deve atualizar status com sucesso quando a transição for válida")
    void deveAtualizarStatusQuandoTransicaoForValida() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setUsuarioId("user_001");
        
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId("user_001");
        usuarioMock.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);
        
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PAGO);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, usuarioMock);

        verify(pedidoRepository, times(1)).save(pedidoMock);
        assertEquals(StatusPedido.PREPARANDO, pedidoMock.getStatus());
    }

    @Test
    @DisplayName("Deve lançar exceção quando a transição for inválida")
    void deveLancarExcecaoQuandoTransicaoForInvalida() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setUsuarioId("user_001");
        
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId("user_001");
        usuarioMock.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);
        
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.ENTREGUE);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        br.com.nhac.backend_nhac.exceptions.TransicaoStatusInvalidaException excecao = assertThrows(br.com.nhac.backend_nhac.exceptions.TransicaoStatusInvalidaException.class, () -> {
            pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, usuarioMock);
        });

        assertTrue(excecao.getMessage().contains("Transição de status inválida"));
    }

    @Test
    @DisplayName("Deve lançar exceção quando status atual for igual ao novo")
    void deveLancarExcecaoQuandoStatusAtualIgualNovo() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setUsuarioId("user_001");
        
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId("user_001");
        usuarioMock.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);
        
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PREPARANDO);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        br.com.nhac.backend_nhac.exceptions.TransicaoStatusInvalidaException excecao = assertThrows(br.com.nhac.backend_nhac.exceptions.TransicaoStatusInvalidaException.class, () -> {
            pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, usuarioMock);
        });

        assertTrue(excecao.getMessage().contains("já está no status"));
    }

    @Test
    @DisplayName("Deve cancelar pedido quando for o dono e o status for PENDENTE")
    void deveCancelarPedidoQuandoForODonoEOStatusPermitir() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_123");
        pedidoMock.setStatus(StatusPedido.PENDENTE);

        when(pedidoRepository.findLockedById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        pedidoService.cancelarPedido("pedido_123", "user_123");

        verify(pedidoRepository, times(1)).save(pedidoMock);
        assertEquals(StatusPedido.CANCELADO, pedidoMock.getStatus());
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException ao tentar cancelar pedido de outro usuário")
    void deveLancarAcessoNegadoAoTentarCancelarPedidoDeOutroUsuario() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_diferente");

        when(pedidoRepository.findLockedById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        assertThrows(AcessoNegadoException.class, () -> {
            pedidoService.cancelarPedido("pedido_123", "user_123");
        });
    }

    @Test
    @DisplayName("Deve lançar TransicaoStatusInvalidaException ao tentar cancelar pedido que já saiu para entrega")
    void deveLancarRegraDeNegocioAoTentarCancelarPedidoQueJaSaiuParaEntrega() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setUsuarioId("user_123");
        pedidoMock.setStatus(StatusPedido.SAIU_ENTREGA);

        when(pedidoRepository.findLockedById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        assertThrows(RegraDeNegocioException.class, () -> {
            pedidoService.cancelarPedido("pedido_123", "user_123");
        });
    }

    @Test
    @DisplayName("Deve cancelar pedido por falha de pagamento quando o status permitir")
    void deveCancelarPedidoPorFalhaDePagamento() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PENDENTE);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        pedidoService.marcarComoCanceladoPorFalhaDePagamento("pedido_123");

        verify(pedidoRepository, times(1)).save(pedidoMock);
        assertEquals(StatusPedido.CANCELADO, pedidoMock.getStatus());
    }

    @Test
    @DisplayName("Deve lançar TransicaoStatusInvalidaException ao tentar cancelar pedido por falha de pagamento que já saiu para entrega")
    void deveLancarRegraAoCancelarPorFalhaPagamentoPedidoQueJaSaiu() {
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.SAIU_ENTREGA);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        assertThrows(RegraDeNegocioException.class, () -> {
            pedidoService.marcarComoCanceladoPorFalhaDePagamento("pedido_123");
        });
    }

    @Test
    @DisplayName("Deve lançar AcessoNegadoException quando lojista não for dono da loja do pedido")
    void deveLancarAcessoNegadoQuandoLojistaNaoForDonoDaLoja() {
        Loja lojaDoPedido = new Loja();
        lojaDoPedido.setId("loja_pedido");
        lojaDoPedido.setUsuarioId("dono_loja_pedido");

        Usuario lojistaIntruso = new Usuario();
        lojistaIntruso.setId("intruso");
        lojistaIntruso.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);

        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PAGO);
        pedidoMock.setLoja(lojaDoPedido);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        assertThrows(AcessoNegadoException.class, () -> {
            pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, lojistaIntruso);
        });
    }

    @Test
    @DisplayName("Deve permitir que ADMIN atualize status mesmo sem ser dono da loja (bypass)")
    void devePermitirAdminAtualizarStatusSemSerDono() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setUsuarioId("dono_loja");

        Usuario adminMock = new Usuario();
        adminMock.setId("admin_user");
        adminMock.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.ADMIN);

        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PAGO);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, adminMock);

        verify(pedidoRepository, times(1)).save(pedidoMock);
        assertEquals(StatusPedido.PREPARANDO, pedidoMock.getStatus());
    }

    @Test
    @DisplayName("Deve permitir que lojista dono atualize status do pedido")
    void devePermitirLojistaDonoAtualizarStatus() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        lojaMock.setUsuarioId("dono_loja");

        Usuario lojistaDono = new Usuario();
        lojistaDono.setId("dono_loja");
        lojistaDono.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);

        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.PAGO);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        pedidoService.atualizarStatus("pedido_123", StatusPedido.PREPARANDO, lojistaDono);

        verify(pedidoRepository, times(1)).save(pedidoMock);
        assertEquals(StatusPedido.PREPARANDO, pedidoMock.getStatus());
    }

    @Test
    @DisplayName("Não deve permitir atualizar status para ENTREGUE via endpoint de lojista/admin")
    void atualizarStatusParaEntregueDeveSerRecusado() {
        Loja lojaMock = new Loja();
        lojaMock.setId("loja_001");
        
        Usuario adminMock = new Usuario();
        adminMock.setId("admin_user");
        adminMock.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.ADMIN);
        
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId("pedido_123");
        pedidoMock.setStatus(StatusPedido.SAIU_ENTREGA);
        pedidoMock.setLoja(lojaMock);

        when(pedidoRepository.findById("pedido_123")).thenReturn(Optional.of(pedidoMock));

        RegraDeNegocioException excecao = assertThrows(RegraDeNegocioException.class, () -> {
            pedidoService.atualizarStatus("pedido_123", StatusPedido.ENTREGUE, adminMock);
        });

        assertTrue(excecao.getMessage().contains("A entrega só pode ser concluída pelo entregador com o código de confirmação."));
        verify(pedidoRepository, never()).save(pedidoMock);
    }

    @Test
    void naoDeveCancelarPedidoPagoSemEstorno() {
        Usuario dono = new Usuario();
        dono.setId("dono_loja");
        dono.setPapel(br.com.nhac.backend_nhac.domain.usuario.Papel.LOJISTA);
        Loja loja = new Loja();
        loja.setId("loja_1");
        Pedido pedido = new Pedido();
        pedido.setId("pedido_pago");
        pedido.setLoja(loja);
        pedido.setStatus(StatusPedido.PAGO);
        when(pedidoRepository.findById("pedido_pago")).thenReturn(Optional.of(pedido));

        assertThrows(RegraDeNegocioException.class,
                () -> pedidoService.atualizarStatus("pedido_pago", StatusPedido.CANCELADO, dono));
        assertEquals(StatusPedido.PAGO, pedido.getStatus());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Webhook de pagamento repetido deve ser idempotente")
    void webhookPagamentoRepetidoDeveSerIdempotente() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_webhook");
        pedido.setStatus(StatusPedido.PAGO);

        when(pedidoRepository.findByStripePaymentIntentId("pi_repetido"))
                .thenReturn(Optional.of(pedido));

        for (int i = 0; i < 10; i++) {
            pedidoService.marcarComoPagoPorPaymentIntentId("pi_repetido");
        }

        verify(pedidoRepository, never()).save(any(Pedido.class));
        assertEquals(StatusPedido.PAGO, pedido.getStatus());
    }

    @Test
    @DisplayName("Webhook de falha repetido não deve devolver estoque novamente")
    void webhookFalhaRepetidoDeveSerIdempotente() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_cancelado");
        pedido.setStatus(StatusPedido.CANCELADO);

        when(pedidoRepository.findById("ped_cancelado")).thenReturn(Optional.of(pedido));

        for (int i = 0; i < 10; i++) {
            pedidoService.marcarComoCanceladoPorFalhaDePagamento("ped_cancelado");
        }

        verify(produtoRepository, never()).save(any(Produto.class));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

}
