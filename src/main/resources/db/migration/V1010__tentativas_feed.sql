CREATE TABLE tb_feed_tentativas (
    id VARCHAR(64) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    recurso_id VARCHAR(50) NOT NULL
);
CREATE INDEX idx_feed_comentarios_autor ON tb_feed_comentarios(post_id, usuario_id, criado_em, id);
CREATE INDEX idx_feed_destaques ON tb_feed_posts(salvos, curtidas, criado_em, id);
