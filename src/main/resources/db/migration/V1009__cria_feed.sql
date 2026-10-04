CREATE TABLE tb_feed_posts (
    id VARCHAR(50) PRIMARY KEY,
    usuario_id VARCHAR(50) NOT NULL,
    loja_id VARCHAR(50),
    conteudo VARCHAR(5000) NOT NULL,
    patrocinado BOOLEAN NOT NULL DEFAULT FALSE,
    sponsor_label VARCHAR(100),
    curtidas BIGINT NOT NULL DEFAULT 0,
    comentarios BIGINT NOT NULL DEFAULT 0,
    salvos BIGINT NOT NULL DEFAULT 0,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    CONSTRAINT fk_feed_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id),
    CONSTRAINT fk_feed_loja FOREIGN KEY (loja_id) REFERENCES tb_lojas(id) ON DELETE SET NULL,
    CONSTRAINT ck_feed_contagens CHECK (curtidas >= 0 AND comentarios >= 0 AND salvos >= 0),
    INDEX idx_feed_novidades (criado_em, id),
    INDEX idx_feed_promocoes (patrocinado, criado_em, id),
    INDEX idx_feed_em_alta (curtidas, comentarios, criado_em, id)
);
CREATE TABLE tb_feed_imagens (
    post_id VARCHAR(50) NOT NULL,
    ordem INT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (post_id, ordem),
    CONSTRAINT fk_feed_imagem_post FOREIGN KEY (post_id) REFERENCES tb_feed_posts(id) ON DELETE CASCADE
);
CREATE TABLE tb_feed_hashtags (
    post_id VARCHAR(50) NOT NULL,
    ordem INT NOT NULL,
    tag VARCHAR(60) NOT NULL,
    PRIMARY KEY (post_id, ordem),
    CONSTRAINT fk_feed_tag_post FOREIGN KEY (post_id) REFERENCES tb_feed_posts(id) ON DELETE CASCADE
);
CREATE TABLE tb_feed_comentarios (
    id VARCHAR(50) PRIMARY KEY,
    post_id VARCHAR(50) NOT NULL,
    usuario_id VARCHAR(50) NOT NULL,
    conteudo VARCHAR(2000) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    CONSTRAINT fk_feed_comentario_post FOREIGN KEY (post_id) REFERENCES tb_feed_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_comentario_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id),
    INDEX idx_feed_comentarios (post_id, criado_em, id)
);
CREATE TABLE tb_feed_interacoes (
    id VARCHAR(50) PRIMARY KEY,
    post_id VARCHAR(50) NOT NULL,
    usuario_id VARCHAR(50) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    CONSTRAINT uk_feed_interacao UNIQUE (post_id, usuario_id, tipo),
    CONSTRAINT fk_feed_interacao_post FOREIGN KEY (post_id) REFERENCES tb_feed_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_interacao_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id),
    INDEX idx_feed_interacoes_usuario (usuario_id, tipo, post_id)
);
