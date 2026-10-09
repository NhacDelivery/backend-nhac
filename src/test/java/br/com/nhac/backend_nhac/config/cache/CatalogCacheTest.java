package br.com.nhac.backend_nhac.config.cache;

import br.com.nhac.backend_nhac.domain.avaliacao.*;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoAvaliacaoResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.exceptions.*;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;

import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring service proxies, Caffeine and H2 transactions; only repositories are mocked. */
class CatalogCacheTest {
    AnnotationConfigApplicationContext context;
    ProdutoRepository products;
    LojaRepository shops;
    UsuarioRepository users;
    AvaliacaoRepository reviews;
    PedidoRepository orders;
    ProdutoService productService;
    LojaService shopService;
    CacheManager caches;
    TransactionTemplate transaction;
    Loja shop;
    Produto product;
    Usuario admin;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean
        DataSourceTransactionManager transactionManager() {
            return new DataSourceTransactionManager(new DriverManagerDataSource(
                    "jdbc:h2:mem:cache-test;DB_CLOSE_DELAY=-1", "sa", ""));
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("conversionService", org.springframework.boot.convert.ApplicationConversionService.class);
        products = mock(ProdutoRepository.class);
        shops = mock(LojaRepository.class);
        users = mock(UsuarioRepository.class);
        reviews = mock(AvaliacaoRepository.class);
        orders = mock(PedidoRepository.class);
        context.registerBean(ProdutoRepository.class, () -> products);
        context.registerBean(LojaRepository.class, () -> shops);
        context.registerBean(UsuarioRepository.class, () -> users);
        context.registerBean(AvaliacaoRepository.class, () -> reviews);
        context.registerBean(PedidoRepository.class, () -> orders);
        context.registerBean(LojaAccessService.class, () -> mock(LojaAccessService.class));
        context.register(CacheConfiguration.class, Transactions.class,
                ProdutoService.class, LojaService.class, AvaliacaoService.class, FreteService.class);
        context.refresh();
        productService = context.getBean(ProdutoService.class);
        shopService = context.getBean(LojaService.class);
        caches = context.getBean(CacheManager.class);
        transaction = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        shop = Loja.builder().id("shop").nome("Loja").isAberto(true)
                .dadosOperacionais(new DadosOperacionais(0f, BigDecimal.ZERO, 10, 20, 0, true, false, null))
                .formasPagamento(new FormasPagamento(true, true, true, true, false, false)).build();
        product = new Produto();
        product.setId("product");
        product.setLoja(shop);
        product.setNome("Produto");
        product.setAtivo(true);
        product.setAdicionais(new ArrayList<>());
        admin = new Usuario();
        admin.setId("admin");
        admin.setPapel(Papel.ADMIN);
        when(products.findByIdAndIsAtivoTrue("product")).thenAnswer(i ->
                product.isAtivo() ? Optional.of(product) : Optional.empty());
        when(products.findById("product")).thenReturn(Optional.of(product));
        when(shops.findById("shop")).thenReturn(Optional.of(shop));
        when(shops.findByIdAndIsAbertoTrue("shop")).thenAnswer(i ->
                shop.isAberto() ? Optional.of(shop) : Optional.empty());
        when(shops.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void repeatedDetailsHitCacheAndDeactivationInvalidates() {
        assertEquals("Produto", productService.buscarProdutoPorId("product").nome());
        productService.buscarProdutoPorId("product");
        verify(products, times(1)).findByIdAndIsAtivoTrue("product");
        productService.desativarProduto("product", admin);
        assertThrows(IdNaoEncontradoException.class, () -> productService.buscarProdutoPorId("product"));
        productService.ativarProduto("product", admin);
        assertEquals("Produto", productService.buscarProdutoPorId("product").nome());
        verify(products, times(3)).findByIdAndIsAtivoTrue("product");
    }

    @Test
    void fullFiltersPageAndSortBelongToCacheKey() {
        when(products.findAllWithFilters(any(), any(), any(), any(), any()))
                .thenAnswer(i -> Page.empty(i.getArgument(4)));
        var first = PageRequest.of(0, 10, Sort.by("nome"));
        var second = PageRequest.of(1, 10, Sort.by("nome"));
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "pizza", first);
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "pizza", first);
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "pizza", second);
        productService.listarProdutos("other", BigDecimal.TEN, "comida", "pizza", first);
        productService.listarProdutos("shop", BigDecimal.ONE, "comida", "pizza", first);
        productService.listarProdutos("shop", BigDecimal.TEN, "bebida", "pizza", first);
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "suco", first);
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "pizza", PageRequest.of(0, 10, Sort.by("preco")));
        productService.listarProdutos("shop", BigDecimal.TEN, "comida", "pizza", PageRequest.of(0, 20, Sort.by("nome")));
        verify(products, times(8)).findAllWithFilters(any(), any(), any(), any(), any());
    }

    @Test
    void oversizedAndUnpagedListsBypassCache() {
        when(products.findAllWithFilters(isNull(), isNull(), isNull(), isNull(), any()))
                .thenAnswer(i -> Page.empty(i.getArgument(4)));
        for (Pageable page : List.of(Pageable.unpaged(), PageRequest.of(0, 101), PageRequest.of(20, 10))) {
            productService.listarProdutos(null, null, null, null, page);
            productService.listarProdutos(null, null, null, null, page);
        }
        verify(products, times(6)).findAllWithFilters(isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void shopListsCacheButLocationSearchDoesNot() {
        when(shops.findByIsAbertoTrue(any())).thenReturn(Page.empty());
        when(shops.buscarLojasComFiltros(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        for (int i = 0; i < 2; i++) {
            shopService.obterLojasPaginadas(null, null, null, null, 0, 10);
            shopService.obterLojasPaginadas(null, -23.0, -46.0, 5.0, 0, 10);
        }
        verify(shops, times(1)).findByIsAbertoTrue(any());
        verify(shops, times(2)).buscarLojasComFiltros(any(), any(), any(), any(), any());
    }

    @Test
    void closingShopEvictsShopAndProductViewsOnlyAfterCommit() {
        shopService.obterLojaId("shop");
        productService.buscarProdutoPorId("product");
        caches.getCache(LOJAS).put("page", "cached");
        caches.getCache(PRODUTOS).put("page", "cached");
        transaction.executeWithoutResult(status -> {
            shopService.atualizarAbertura("shop", false, admin);
            assertNotNull(caches.getCache(LOJA).get("shop"));
            assertNotNull(caches.getCache(PRODUTO).get("product"));
        });
        assertNull(caches.getCache(LOJA).get("shop"));
        assertNull(caches.getCache(PRODUTO).get("product"));
        assertNull(caches.getCache(LOJAS).get("page"));
        assertNull(caches.getCache(PRODUTOS).get("page"));
        assertFalse(productService.buscarProdutoPorId("product").lojaAberta());
        assertFalse(shopService.obterLojaId("shop").isAberto());
    }

    @Test
    void rollbackDoesNotEvictExistingCache() {
        productService.buscarProdutoPorId("product");
        transaction.executeWithoutResult(status -> {
            productService.desativarProduto("product", admin);
            status.setRollbackOnly();
        });
        assertNotNull(caches.getCache(PRODUTO).get("product"));
    }

    @Test
    void uncommittedReadIsNotPublishedOnRollback() {
        transaction.executeWithoutResult(status -> {
            productService.buscarProdutoPorId("product");
            assertNull(caches.getCache(PRODUTO).get("product"));
            status.setRollbackOnly();
        });
        assertNull(caches.getCache(PRODUTO).get("product"));
    }

    @Test
    void cacheHitDoesNotBypassMutationAuthorization() {
        productService.buscarProdutoPorId("product");
        Usuario outsider = new Usuario();
        outsider.setPapel(Papel.LOJISTA);
        assertThrows(AcessoNegadoException.class, () -> productService.desativarProduto("product", outsider));
        assertNotNull(caches.getCache(PRODUTO).get("product"));
        verify(products, never()).save(any());
    }

    @Test
    void missingProductIsNotNegativelyCached() {
        assertThrows(IdNaoEncontradoException.class, () -> productService.buscarProdutoPorId("missing"));
        assertThrows(IdNaoEncontradoException.class, () -> productService.buscarProdutoPorId("missing"));
        verify(products, times(2)).findByIdAndIsAtivoTrue("missing");
    }

    @Test
    void newReviewInvalidatesAggregatesAndShopViews() {
        when(products.existsById("product")).thenReturn(true);
        when(products.getResumoAvaliacoesPorProdutoId("product"))
                .thenReturn(new ProdutoAvaliacaoResumoDTO(0L, null));
        productService.buscarResumoAvaliacoes("product");
        productService.buscarResumoAvaliacoes("product");
        verify(products, times(1)).getResumoAvaliacoesPorProdutoId("product");
        shopService.obterLojaId("shop");
        caches.getCache(LOJAS).put("page", "cached");
        Pedido order = new Pedido();
        order.setId("order");
        order.setUsuarioId("admin");
        order.setLoja(shop);
        order.setStatus(StatusPedido.ENTREGUE);
        when(users.findById("admin")).thenReturn(Optional.of(admin));
        when(orders.findById("order")).thenReturn(Optional.of(order));
        when(shops.findLockedById("shop")).thenReturn(Optional.of(shop));
        when(reviews.countByLojaId("shop")).thenReturn(1L);
        when(reviews.calcularMediaPorLojaId("shop")).thenReturn(5.0);
        context.getBean(AvaliacaoService.class).criarAvaliacao("admin", new AvaliacaoCreateDTO("order", 5, "Bom"));
        assertNull(caches.getCache(PRODUTO_AVALIACOES).get("product"));
        assertNull(caches.getCache(LOJA).get("shop"));
        assertNull(caches.getCache(LOJAS).get("page"));
    }
}
