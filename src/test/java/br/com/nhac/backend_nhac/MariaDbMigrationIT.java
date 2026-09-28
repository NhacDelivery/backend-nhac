package br.com.nhac.backend_nhac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;

class MariaDbMigrationIT {

    @Test
    void deveAplicarTodasAsMigrationsEmMariaDbLimpo() {
        try (MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
                .withDatabaseName("nhac_migrations")
                .withUsername("nhac")
                .withPassword("nhac_test")) {

            mariadb.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                    .locations("classpath:db/migration")
                    .validateOnMigrate(true)
                    .outOfOrder(false)
                    .load();

            var result = flyway.migrate();

            assertEquals("1005", result.targetSchemaVersion);
            assertEquals("1005", flyway.info().current().getVersion().getVersion());
        }
    }
}
