CREATE TABLE tb_dispositivos_push (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    usuario_id VARCHAR(50) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token VARCHAR(2048) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_dispositivos_push_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_dispositivos_push_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id)
);
CREATE INDEX idx_dispositivos_push_usuario ON tb_dispositivos_push(usuario_id);
