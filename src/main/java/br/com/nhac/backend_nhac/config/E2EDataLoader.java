package br.com.nhac.backend_nhac.config;

import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.FormasPagamento;
import br.com.nhac.backend_nhac.domain.loja.GeoLocalizacao;
import br.com.nhac.backend_nhac.domain.loja.HorariosFuncionamento;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.pedido.EnderecoEntrega;
import br.com.nhac.backend_nhac.domain.pedido.ItemPedido;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.EnderecoUsuario;
import br.com.nhac.backend_nhac.domain.usuario.EnderecoUsuarioRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("e2e")
public class E2EDataLoader implements CommandLineRunner {

    public static final String USER_ID = "e2e-cliente-001";
    public static final String MERCHANT_ID = "e2e-lojista-001";
    public static final String ORDER_CUSTOMER_ID = "e2e-cliente-lojista-001";
    public static final String STORE_ID = "e2e-loja-001";
    public static final String PRODUCT_ID = "e2e-produto-001";
    public static final String MERCHANT_ORDER_ID = "e2e-pedido-lojista-001";

    @org.springframework.beans.factory.annotation.Value("${E2E_PASSWORD:}")
    private String fixturePassword;
    private String senhaFixture() {
        if (fixturePassword == null || fixturePassword.length()<12) throw new IllegalStateException("Defina E2E_PASSWORD com uma senha temporária de pelo menos 12 caracteres para o backend isolado.");
        return fixturePassword;
    }
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final EnderecoUsuarioRepository enderecoRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final PasswordEncoder passwordEncoder;

    public E2EDataLoader(
            JdbcTemplate jdbcTemplate,
            UsuarioRepository usuarioRepository,
            EnderecoUsuarioRepository enderecoRepository,
            LojaRepository lojaRepository,
            ProdutoRepository produtoRepository,
            PedidoRepository pedidoRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.enderecoRepository = enderecoRepository;
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        limparDadosDaAplicacao();

        Usuario cliente = new Usuario();
        cliente.setId(USER_ID);
        cliente.setNome("Cliente E2E");
        cliente.setEmail("e2e.cliente@nhac.local");
        cliente.setTelefone("+5511999990001");
        cliente.setImagemUrl("");
        cliente.setSenha(passwordEncoder.encode(senhaFixture()));
        cliente.setEnderecos(new ArrayList<>());
        cliente.setPapel(Papel.CLIENTE);
        cliente.setTelefoneVerificado(true);
        cliente.setEmailVerificado(true);
        cliente.setAtivo(true);
        usuarioRepository.saveAndFlush(cliente);

        Usuario lojista = novoUsuario(
                MERCHANT_ID,
                "Lojista E2E",
                "e2e.lojista@nhac.local",
                "+5511999990002",
                Papel.LOJISTA
        );
        usuarioRepository.saveAndFlush(lojista);

        Usuario clientePedidoLojista = novoUsuario(
                ORDER_CUSTOMER_ID,
                "Cliente do Pedido E2E",
                "e2e.pedido@nhac.local",
                "+5511999990003",
                Papel.CLIENTE
        );
        usuarioRepository.saveAndFlush(clientePedidoLojista);

        EnderecoUsuario endereco = new EnderecoUsuario(
                "e2e-endereco-001",
                cliente,
                "Praça da Sé",
                "100",
                "Sé",
                "São Paulo",
                "SP",
                "01001-000",
                "Fixture E2E",
                true
        );
        enderecoRepository.saveAndFlush(endereco);

        DadosOperacionais operacao = new DadosOperacionais();
        operacao.setAvaliacaoMedia(5.0f);
        operacao.setTaxaEntregaBase(new BigDecimal("5.00"));
        operacao.setTempoEntregaMin(20);
        operacao.setTempoEntregaMax(30);
        operacao.setTotalAvaliacoes(1);
        operacao.setEntregaPropria(true);
        operacao.setRetiradaNoLocal(false);
        operacao.setRaioEntregaKm(new BigDecimal("20.00"));

        HorariosFuncionamento horarios = new HorariosFuncionamento(
                "00:00-23:59", "00:00-23:59", "00:00-23:59",
                "00:00-23:59", "00:00-23:59", "00:00-23:59",
                "00:00-23:59"
        );

        Loja loja = Loja.builder()
                .id(STORE_ID)
                .usuarioId(MERCHANT_ID)
                .nome("Loja E2E")
                .descricao("Loja determinística para testes E2E")
                .categoria("E2E")
                .imagemUrl("")
                .isAberto(true)
                .dadosOperacionais(operacao)
                .endereco(new EnderecoLoja(
                        "Praça da Sé", "1", "São Paulo", "SP", "01001-000",
                        "Sé", "Fixture E2E"))
                .geoLocalizacao(new GeoLocalizacao(-23.550520, -46.633308, "6gyf4"))
                .horariosFuncionamento(horarios)
                .formasPagamento(new FormasPagamento(true, true, true, true, false, false))
                .build();
        lojaRepository.saveAndFlush(loja);

        Produto produto = new Produto();
        produto.setId(PRODUCT_ID);
        produto.setLoja(loja);
        produto.setNome("Produto E2E");
        produto.setDescricao("Produto determinístico para o happy path E2E");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setCategoriaMenu("Prato Principal");
        produto.setImagemUrl("");
        produto.setAtivo(true);
        produto.setCriadoEm(Instant.parse("2026-01-01T00:00:00Z"));
        produto.setPeso("300g");
        produto.setPercentualDesconto(0);
        produto.setEstoque(20);
        produtoRepository.saveAndFlush(produto);

        Pedido pedidoLojista = new Pedido();
        pedidoLojista.setId(MERCHANT_ORDER_ID);
        pedidoLojista.setUsuarioId(ORDER_CUSTOMER_ID);
        pedidoLojista.setLoja(loja);
        pedidoLojista.setValorTotal(new BigDecimal("30.00"));
        pedidoLojista.setTaxaFrete(new BigDecimal("5.00"));
        pedidoLojista.setFormaPagamento("DINHEIRO");
        pedidoLojista.setTrocoPara(null);
        pedidoLojista.setObservacao("Pedido determinístico para o E2E do lojista");
        pedidoLojista.setStatus(StatusPedido.PAGO);
        pedidoLojista.setEnderecoEntrega(new EnderecoEntrega(
                "Praça da Sé", "100", "Sé", "São Paulo", "SP", "01001-000", "Fixture E2E"
        ));
        pedidoLojista.setEntregaLatitude(-23.550520);
        pedidoLojista.setEntregaLongitude(-46.633308);
        pedidoLojista.setCriadoEm(Instant.parse("2026-01-01T12:00:00Z"));
        pedidoLojista.setDesconto(BigDecimal.ZERO);

        ItemPedido itemPedido = new ItemPedido();
        itemPedido.setId("e2e-item-lojista-001");
        itemPedido.setProduto(produto);
        itemPedido.setNome(produto.getNome());
        itemPedido.setImagemUrl(produto.getImagemUrl());
        itemPedido.setPrecoHistorico(produto.getPreco());
        itemPedido.setQuantidade(1);
        pedidoLojista.adicionarItem(itemPedido);
        pedidoRepository.saveAndFlush(pedidoLojista);
    }

    private Usuario novoUsuario(String id, String nome, String email, String telefone, Papel papel) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setTelefone(telefone);
        usuario.setImagemUrl("");
        usuario.setSenha(passwordEncoder.encode(senhaFixture()));
        usuario.setEnderecos(new ArrayList<>());
        usuario.setPapel(papel);
        usuario.setTelefoneVerificado(true);
        usuario.setEmailVerificado(true);
        usuario.setAtivo(true);
        return usuario;
    }

    private void limparDadosDaAplicacao() {
        jdbcTemplate.execute((Connection connection) -> {
            List<String> tabelas = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                 "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
                while (result.next()) tabelas.add(result.getString(1));
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String tabela : tabelas) {
                    if (!"flyway_schema_history".equalsIgnoreCase(tabela)
                            && tabela.matches("[A-Za-z0-9_]+")) {
                        statement.execute("TRUNCATE TABLE `" + tabela + "`");
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }
}
