package br.com.nhac.backend_nhac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

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

            var pending = flyway.info().pending();
            assertTrue(pending.length > 0, "O banco limpo deve ter migrations pendentes");
            var latestVersion = Arrays.stream(pending)
                    .map(migration -> migration.getVersion())
                    .max((left, right) -> left.compareTo(right))
                    .orElseThrow();

            var result = flyway.migrate();

            assertEquals(pending.length, result.migrationsExecuted);
            assertEquals(latestVersion.getVersion(), result.targetSchemaVersion);
            assertEquals(latestVersion, flyway.info().current().getVersion());
            assertEquals(0, flyway.info().pending().length);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted,
                    "Executar novamente não deve reaplicar migrations");
        }
    }
}
