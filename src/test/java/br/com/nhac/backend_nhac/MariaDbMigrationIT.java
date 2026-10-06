package br.com.nhac.backend_nhac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;

class MariaDbMigrationIT {
    private static long contagem(java.sql.Statement statement, String tabela) throws java.sql.SQLException {
        try (var rs = statement.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            rs.next(); return rs.getLong(1);
        }
    }

    private static void verificarCatalogoSocial(MariaDBContainer<?> db) throws java.sql.SQLException {
        try (var connection = java.sql.DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement = connection.createStatement()) {
            String owner;
            try (var rs = statement.executeQuery("SELECT usuario_id FROM tb_lojas WHERE id='loja_0002'")) {
                assertTrue(rs.next()); owner = rs.getString(1);
            }
            statement.executeUpdate("UPDATE tb_lojas SET usuario_id=NULL WHERE id='loja_0001'");
            long lojas = contagem(statement, "tb_lojas");
            String script = br.com.nhac.backend_nhac.util.GeradorCatalogoInsano.gerarComplementoLojasExistentes(
                    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            long usuarios = 0;
            for (int rodada = 0; rodada < 2; rodada++) {
                for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
                assertEquals(lojas, contagem(statement, "tb_lojas"));
                assertEquals(lojas * 2, contagem(statement, "tb_feed_posts"));
                assertEquals(lojas * 3, contagem(statement, "tb_avaliacoes"));
                assertEquals(lojas * 3, contagem(statement, "tb_pedidos"));
                if (rodada == 0) usuarios = contagem(statement, "tb_usuarios");
                else assertEquals(usuarios, contagem(statement, "tb_usuarios"));
            }
            try (var rs = statement.executeQuery("SELECT usuario_id FROM tb_lojas WHERE id='loja_0002'")) {
                assertTrue(rs.next()); assertEquals(owner, rs.getString(1));
            }
            try (var rs = statement.executeQuery("SELECT COUNT(*) FROM tb_usuarios WHERE id LIKE 'demo-%' AND (telefone IS NOT NULL OR telefone_verificado=TRUE)")) {
                assertTrue(rs.next()); assertEquals(0, rs.getLong(1));
            }
            try (var rs = statement.executeQuery("SELECT l.total_avaliacoes,l.avaliacao_media,u.papel FROM tb_lojas l JOIN tb_usuarios u ON u.id=l.usuario_id WHERE l.id='loja_0001'")) {
                assertTrue(rs.next()); assertEquals(3, rs.getInt(1));
                assertEquals(4.7, rs.getDouble(2), 0.01); assertEquals("LOJISTA", rs.getString(3));
            }
        }
    }


    @Test
    void deveAplicarTodasAsMigrationsEmMariaDbLimpo() throws java.sql.SQLException {
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
            verificarCatalogoSocial(mariadb);
        }
    }
}
