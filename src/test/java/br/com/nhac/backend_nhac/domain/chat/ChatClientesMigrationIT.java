package br.com.nhac.backend_nhac.domain.chat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ChatClientesMigrationIT {
    @Test
    void upgradePreservaChatDeLojaEProtegeParesDiretos() throws SQLException {
        try (var db = new MariaDBContainer<>("mariadb:11")
                .withDatabaseName("nhac_chat").withUsername("nhac").withPassword(java.util.UUID.randomUUID().toString())) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .locations("classpath:db/migration").target("1013").load().migrate();
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var s = connection.createStatement()) {
                s.executeUpdate("INSERT INTO tb_usuarios (id,nome,email,telefone,senha,papel) VALUES ('chat-a','A','chat-a@teste.com','11900000001','hash','CLIENTE'), ('chat-b','B','chat-b@teste.com','11900000002','hash','CLIENTE')");
                s.executeUpdate("INSERT INTO tb_conversas (id,loja_id,cliente_id,criada_em,ultima_mensagem_em,participante_tipo,nao_lidas_cliente) SELECT 'chat-legado',id,'chat-a',NOW(),NOW(),'CLIENTE',3 FROM tb_lojas LIMIT 1");
                s.executeUpdate("INSERT INTO tb_mensagens (id,conversa_id,remetente_tipo,remetente_usuario_id,conteudo,enviada_em) VALUES ('msg-legada','chat-legado','CLIENTE','chat-a','Preservar',NOW())");
            }
            Flyway flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .locations("classpath:db/migration").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var s = connection.createStatement()) {
                try (var rs = s.executeQuery("SELECT c.nao_lidas_cliente,m.conteudo FROM tb_conversas c JOIN tb_mensagens m ON m.conversa_id=c.id WHERE c.id='chat-legado'")) {
                    assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); assertEquals("Preservar", rs.getString(2));
                }
                String insert = "INSERT INTO tb_conversas (id,cliente_id,segundo_cliente_id,criada_em,ultima_mensagem_em,participante_tipo) VALUES ";
                s.executeUpdate(insert + "('chat-direto','chat-a','chat-b',NOW(),NOW(),'CLIENTE')");
                assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('duplicada','chat-a','chat-b',NOW(),NOW(),'CLIENTE')"));
                assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('self','chat-a','chat-a',NOW(),NOW(),'CLIENTE')"));
                assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('invalida','chat-a','chat-b',NOW(),NOW(),'ENTREGADOR')"));
                assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('sem-canal','chat-a',NULL,NOW(),NOW(),'CLIENTE')"));
                assertThrows(SQLException.class, () -> s.executeUpdate(insert + "('sem-destino','chat-a','ausente',NOW(),NOW(),'CLIENTE')"));
                try (var keys = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, "tb_conversas")) {
                    assertTrue(keys.next()); assertEquals("id", keys.getString("COLUMN_NAME"));
                }
            }
        }
    }
}
