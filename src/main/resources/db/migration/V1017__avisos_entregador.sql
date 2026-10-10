CREATE TABLE tb_avisos_entregador (
 id VARCHAR(100) PRIMARY KEY, usuario_id VARCHAR(50) NOT NULL,
 tipo VARCHAR(30) NOT NULL, texto VARCHAR(200) NOT NULL,
 pedido_id VARCHAR(50), loja_id VARCHAR(50), loja_nome VARCHAR(100), oferta_id VARCHAR(50),
 criado_em TIMESTAMP(6) NOT NULL, push_enviado BOOLEAN NOT NULL DEFAULT FALSE,
 tentativas INTEGER NOT NULL DEFAULT 0, proxima_tentativa TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_aviso_usuario FOREIGN KEY(usuario_id) REFERENCES tb_usuarios(id),
 INDEX idx_aviso_usuario(usuario_id,criado_em),
 INDEX idx_aviso_push(push_enviado,proxima_tentativa)
);
