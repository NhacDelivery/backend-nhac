package br.com.nhac.backend_nhac.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.GeoLocalizacao;
import br.com.nhac.backend_nhac.domain.loja.HorariosFuncionamento;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;

@Component
@Profile("dev")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    public DataLoader(LojaRepository lojaRepository,
                      ProdutoRepository produtoRepository,
                      UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder) {
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {

        // Usuários de desenvolvimento com emailVerificado/telefoneVerificado=true.
        if (usuarioRepository.count() == 0) {
            log.info("DataLoader v2: banco sem usuários. Cadastrando usuários de teste...");

            Usuario cliente = new Usuario();
            cliente.setId("firebase_user_cliente_001");
            cliente.setNome("Matheus Alves Cliente");
            cliente.setEmail("cliente@nhac.com");
            cliente.setTelefone("+5511999998888");
            cliente.setImagemUrl("https://picsum.photos/seed/cliente/200");
            cliente.setSenha(passwordEncoder.encode("senha123"));
            cliente.setEnderecos(new ArrayList<>());
            cliente.setPapel(Papel.CLIENTE);
            cliente.setTelefoneVerificado(true);
            cliente.setEmailVerificado(true);
            cliente.setAtivo(true);
            usuarioRepository.save(cliente);

            Usuario lojista = new Usuario();
            lojista.setId("firebase_user_lojista_001");
            lojista.setNome("Carlos Lojista");
            lojista.setEmail("lojista@nhac.com");
            lojista.setTelefone("+5511988887777");
            lojista.setImagemUrl("https://picsum.photos/seed/lojista/200");
            lojista.setSenha(passwordEncoder.encode("lojista123"));
            lojista.setEnderecos(new ArrayList<>());
            lojista.setPapel(Papel.LOJISTA);
            lojista.setTelefoneVerificado(true);
            lojista.setEmailVerificado(true);
            lojista.setAtivo(true);
            usuarioRepository.save(lojista);

            Usuario funcionario = new Usuario();
            funcionario.setId("firebase_user_funcionario_001");
            funcionario.setNome("Ana Funcionária");
            funcionario.setEmail("funcionario@nhac.com");
            funcionario.setTelefone("+5511977776666");
            funcionario.setImagemUrl("https://picsum.photos/seed/funcionario/200");
            funcionario.setSenha(passwordEncoder.encode("func123"));
            funcionario.setEnderecos(new ArrayList<>());
            funcionario.setPapel(Papel.LOJISTA);
            funcionario.setTelefoneVerificado(true);
            funcionario.setEmailVerificado(true);
            funcionario.setAtivo(true);
            usuarioRepository.save(funcionario);

            Usuario admin = new Usuario();
            admin.setId("firebase_user_admin_001");
            admin.setNome("Admin Nhac");
            admin.setEmail("admin@nhac.com");
            admin.setTelefone("+5511966665555");
            admin.setImagemUrl("https://picsum.photos/seed/admin/200");
            admin.setSenha(passwordEncoder.encode("admin123"));
            admin.setEnderecos(new ArrayList<>());
            admin.setPapel(Papel.ADMIN);
            admin.setTelefoneVerificado(true);
            admin.setEmailVerificado(true);
            admin.setAtivo(true);
            usuarioRepository.save(admin);

            log.info("DataLoader v2: usuários criados — logins: cliente@nhac.com / lojista@nhac.com / funcionario@nhac.com / admin@nhac.com (senha: senha123 / lojista123 / func123 / admin123)");
        } else {
            log.info("DataLoader v2: banco já possui {} usuários — pulando criação.", usuarioRepository.count());
        }

        if (lojaRepository.count() > 0) {
            log.info("DataLoader v2: banco já possui {} lojas — pulando seção de lojas.", lojaRepository.count());
            return;
        }

        log.info("DataLoader v2: iniciando cadastro de lojas e produtos de teste...");

        Loja lojaSushi = new Loja();
        lojaSushi.setId("loja_sushi_001");
        lojaSushi.setUsuarioId("firebase_user_lojista_001");
        lojaSushi.setNome("Nhac Sushi Premium");
        lojaSushi.setDescricao("O melhor da culinária japonesa tradicional e contemporânea");
        lojaSushi.setCategoria("Japonesa");
        lojaSushi.setImagemUrl("https://picsum.photos/seed/sushi/400/400");
        lojaSushi.setAberto(true);

        DadosOperacionais dadosSushi = new DadosOperacionais();
        dadosSushi.setAvaliacaoMedia(4.8f);
        dadosSushi.setTotalAvaliacoes(154);
        dadosSushi.setTaxaEntregaBase(new BigDecimal("5.99"));
        dadosSushi.setTempoEntregaMin(20);
        dadosSushi.setTempoEntregaMax(40);
        dadosSushi.setEntregaPropria(true);
        dadosSushi.setRetiradaNoLocal(true);
        lojaSushi.setDadosOperacionais(dadosSushi);

        EnderecoLoja endSushi = new EnderecoLoja();
        endSushi.setRua("Alameda dos Autores");
        endSushi.setNumero("100");
        endSushi.setBairro("Jardim Paulista");
        endSushi.setCidade("São Paulo");
        endSushi.setEstado("SP");
        endSushi.setCep("01000-000");
        lojaSushi.setEndereco(endSushi);

        GeoLocalizacao geoSushi = new GeoLocalizacao();
        geoSushi.setGeoLat(-23.5505);
        geoSushi.setGeoLng(-46.6333);
        geoSushi.setGeoHash("6gyf4");
        lojaSushi.setGeoLocalizacao(geoSushi);

        HorariosFuncionamento horariosSushi = new HorariosFuncionamento();
        horariosSushi.setDomingo("10:00-20:00");
        horariosSushi.setSegunda("08:00-22:00");
        horariosSushi.setTerca("08:00-22:00");
        horariosSushi.setQuarta("08:00-22:00");
        horariosSushi.setQuinta("08:00-22:00");
        horariosSushi.setSexta("08:00-23:00");
        horariosSushi.setSabado("09:00-23:00");
        lojaSushi.setHorariosFuncionamento(horariosSushi);

        Loja lojaHamburguer = new Loja();
        lojaHamburguer.setId("loja_burger_002");
        lojaHamburguer.setUsuarioId("firebase_user_funcionario_001");
        lojaHamburguer.setNome("Nhac Burger & Fries");
        lojaHamburguer.setDescricao("Hambúrgueres artesanais grelhados no fogo como você nunca viu");
        lojaHamburguer.setCategoria("Lanches");
        lojaHamburguer.setImagemUrl("https://picsum.photos/seed/burger/400/400");
        lojaHamburguer.setAberto(true);

        DadosOperacionais dadosBurger = new DadosOperacionais();
        dadosBurger.setAvaliacaoMedia(4.6f);
        dadosBurger.setTotalAvaliacoes(98);
        dadosBurger.setTaxaEntregaBase(new BigDecimal("4.99"));
        dadosBurger.setTempoEntregaMin(25);
        dadosBurger.setTempoEntregaMax(45);
        dadosBurger.setEntregaPropria(true);
        dadosBurger.setRetiradaNoLocal(false);
        lojaHamburguer.setDadosOperacionais(dadosBurger);

        EnderecoLoja endBurger = new EnderecoLoja();
        endBurger.setRua("Avenida dos Sabores");
        endBurger.setNumero("550");
        endBurger.setBairro("Vila Olímpia");
        endBurger.setCidade("São Paulo");
        endBurger.setEstado("SP");
        endBurger.setCep("02000-000");
        lojaHamburguer.setEndereco(endBurger);

        GeoLocalizacao geoBurger = new GeoLocalizacao();
        geoBurger.setGeoLat(-23.5614);
        geoBurger.setGeoLng(-46.6558);
        geoBurger.setGeoHash("6gyf5");
        lojaHamburguer.setGeoLocalizacao(geoBurger);

        HorariosFuncionamento horariosBurger = new HorariosFuncionamento();
        horariosBurger.setDomingo("11:00-21:00");
        horariosBurger.setSegunda("11:00-23:00");
        horariosBurger.setTerca("11:00-23:00");
        horariosBurger.setQuarta("11:00-23:00");
        horariosBurger.setQuinta("11:00-23:00");
        horariosBurger.setSexta("11:00-00:00");
        horariosBurger.setSabado("11:00-00:00");
        lojaHamburguer.setHorariosFuncionamento(horariosBurger);

        lojaRepository.saveAll(List.of(lojaSushi, lojaHamburguer));

        Produto hossomaki = new Produto();
        hossomaki.setId("prod_sushi_001");
        hossomaki.setLoja(lojaSushi);
        hossomaki.setNome("Hossomaki de Salmão");
        hossomaki.setDescricao("Rolinho clássico de salmão");
        hossomaki.setPreco(new BigDecimal("25.50"));
        hossomaki.setCategoriaMenu("Sushi");
        hossomaki.setAtivo(true);
        hossomaki.setCriadoEm(Instant.now());
        hossomaki.setPeso("200g");
        hossomaki.setPercentualDesconto(0);
        hossomaki.setEstoque(100);

        Produto temaki = new Produto();
        temaki.setId("prod_sushi_002");
        temaki.setLoja(lojaSushi);
        temaki.setNome("Temaki de Atum");
        temaki.setDescricao("Cone recheado com atum fresco");
        temaki.setPreco(new BigDecimal("32.00"));
        temaki.setCategoriaMenu("Temaki");
        temaki.setAtivo(true);
        temaki.setCriadoEm(Instant.now());
        temaki.setPeso("180g");
        temaki.setPercentualDesconto(0);
        temaki.setEstoque(100);

        Produto whopper = new Produto();
        whopper.setId("prod_burger_001");
        whopper.setLoja(lojaHamburguer);
        whopper.setNome("Whopper Duplo");
        whopper.setDescricao("Dois hambúrgueres bovinos, queijo e bacon");
        whopper.setPreco(new BigDecimal("45.90"));
        whopper.setCategoriaMenu("Lanches");
        whopper.setAtivo(true);
        whopper.setCriadoEm(Instant.now());
        whopper.setPeso("350g");
        whopper.setPercentualDesconto(0);
        whopper.setEstoque(100);

        Produto batata = new Produto();
        batata.setId("prod_burger_002");
        batata.setLoja(lojaHamburguer);
        batata.setNome("Batata Frita Tradicional");
        batata.setDescricao("Batatas crocantes com sal e alecrim");
        batata.setPreco(new BigDecimal("18.00"));
        batata.setCategoriaMenu("Acompanhamentos");
        batata.setAtivo(true);
        batata.setCriadoEm(Instant.now());
        batata.setPeso("150g");
        batata.setPercentualDesconto(0);
        batata.setEstoque(100);

        produtoRepository.saveAll(List.of(hossomaki, temaki, whopper, batata));

        log.info("DataLoader v2: cadastro finalizado — 2 lojas, 4 produtos.");
    }
}