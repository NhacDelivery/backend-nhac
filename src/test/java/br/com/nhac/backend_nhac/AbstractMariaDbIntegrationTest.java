package br.com.nhac.backend_nhac;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractMariaDbIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    public static void mariaDbProperties(DynamicPropertyRegistry registry) {
        var mariadb = MariaDbTestContainer.instance();

        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "8");
        registry.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_READ_COMMITTED");
    }
}
