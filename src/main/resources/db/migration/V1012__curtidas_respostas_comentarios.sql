ALTER TABLE tb_feed_comentarios ADD COLUMN resposta_a_id VARCHAR(50) NULL;
CREATE TABLE tb_feed_comentario_curtidas (
    comentario_id VARCHAR(50) NOT NULL,
    usuario_id VARCHAR(50) NOT NULL,
    PRIMARY KEY (comentario_id, usuario_id),
    CONSTRAINT fk_feed_comentario_curtida FOREIGN KEY (comentario_id)
        REFERENCES tb_feed_comentarios(id) ON DELETE CASCADE
);
