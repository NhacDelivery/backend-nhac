package br.com.nhac.backend_nhac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected MockMvc mockMvc;

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoEmailRepository codigoVerificacaoEmailRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired private br.com.nhac.backend_nhac.domain.auth.VerificacaoEmailService verificacaoEmailService;
    @Autowired private br.com.nhac.backend_nhac.domain.auth.AuthRateLimitService rateLimiter;

    protected String criarCodigoVerificadoPara(String email) {
        codigoVerificacaoEmailRepository.save(
                br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoEmail.builder()
                        .email(email.trim().toLowerCase())
                        .codigo("123456")
                        .dataExpiracao(java.time.LocalDateTime.now().plusHours(1))
                        .tentativas(0)
                        .utilizado(false)
                        .tipo(br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoEmail.TipoCodigo.CADASTRO)
                        .build()
        );
        return verificacaoEmailService.verificarCodigoCadastro(email, "123456");
    }

    @BeforeEach
    public void setup() {
        limparBanco();
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) org.springframework.test.util.ReflectionTestUtils
                .getField(rateLimiter, "tentativas")).invalidateAll();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /**
     * Limpa TODAS as tabelas do banco entre os testes de integração, desligando a
     * checagem de foreign keys durante o processo.
     *
     * Motivo: cada IT faz seu próprio deleteAll parcial nas entidades que usa, mas a
     * ordem em que o Failsafe executa as classes varia conforme o sistema de arquivos
     * (a lista de classes de teste vem de uma enumeração de diretório), então a ordem
     * local não é igual à do CI. ITs de chat criam linhas em tb_mensagens/tb_conversas
     * referenciando tb_lojas; quando um IT posterior que só limpa
     * pedidos/produtos/lojas/usuarios roda depois deles, o delete em tb_lojas quebra
     * com violação de FK em tb_conversas (era o erro 'loja-conc' no CI).
     * Com a limpeza centralizada aqui (superclasse roda o @BeforeEach antes do das
     * subclasses), qualquer IT começa com o banco inteiro vazio, independente da
     * ordem de execução das classes — e os deleteAll das subclasses continuam
     * funcionando como no-op sobre tabelas já vazias.
     *
     * Suporta H2 (perfil de teste atual) e MySQL/MariaDB (caso os testes passem a
     * rodar contra o mesmo banco do CI/produção). Tudo acontece numa única conexão
     * porque FOREIGN_KEY_CHECKS no MySQL/MariaDB é por sessão.
     */
    private void limparBanco() {
        jdbcTemplate.execute((Connection connection) -> {
            DatabaseMetaData metaData = connection.getMetaData();
            String produto = metaData.getDatabaseProductName() == null
                    ? "" : metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
            boolean h2 = produto.contains("h2");
            boolean mysqlLike = produto.contains("mysql") || produto.contains("mariadb");

            List<String> tabelas = new ArrayList<>();
            String sqlTabelas = h2
                    ? "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'"
                    : "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'";
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sqlTabelas)) {
                while (rs.next()) {
                    tabelas.add(rs.getString(1));
                }
            }

            List<String> alvo = tabelas.stream()
                    .filter(t -> !t.equalsIgnoreCase("flyway_schema_history"))
                    .filter(t -> t.matches("[A-Za-z0-9_]+"))
                    .toList();

            try (Statement st = connection.createStatement()) {
                if (h2) {
                    st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                    for (String tabela : alvo) {
                        st.execute("DELETE FROM \"" + tabela + "\"");
                    }
                    st.execute("SET REFERENTIAL_INTEGRITY TRUE");
                } else if (mysqlLike) {
                    st.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String tabela : alvo) {
                        st.execute("TRUNCATE TABLE `" + tabela + "`");
                    }
                    st.execute("SET FOREIGN_KEY_CHECKS = 1");
                } else {
                    throw new IllegalStateException(
                            "Banco não suportado para a limpeza dos testes de integração: " + produto);
                }
            }
            return null;
        });
    }
}
