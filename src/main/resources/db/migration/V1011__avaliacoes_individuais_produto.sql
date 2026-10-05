CREATE TABLE tb_avaliacoes_produto (
 id VARCHAR(50) PRIMARY KEY, pedido_id VARCHAR(50) NOT NULL, produto_id VARCHAR(50) NOT NULL,
 usuario_id VARCHAR(50) NOT NULL, nota INT NOT NULL, comentario VARCHAR(500), criado_em DATETIME(6) NOT NULL,
 CONSTRAINT uk_avaliacao_produto_pedido UNIQUE(pedido_id,produto_id),
 CONSTRAINT fk_avaliacao_produto_pedido FOREIGN KEY(pedido_id) REFERENCES tb_pedidos(id),
 CONSTRAINT fk_avaliacao_produto_produto FOREIGN KEY(produto_id) REFERENCES tb_produtos(id),
 CONSTRAINT fk_avaliacao_produto_usuario FOREIGN KEY(usuario_id) REFERENCES tb_usuarios(id),
 CONSTRAINT ck_avaliacao_produto_nota CHECK(nota BETWEEN 1 AND 5),
 INDEX idx_avaliacao_produto_data(produto_id,criado_em,id)
);
CREATE TABLE tb_avaliacao_produto_fotos (
 avaliacao_id VARCHAR(50) NOT NULL, ordem INT NOT NULL, url VARCHAR(2048) NOT NULL,
 PRIMARY KEY(avaliacao_id,ordem), CONSTRAINT fk_avaliacao_produto_foto FOREIGN KEY(avaliacao_id) REFERENCES tb_avaliacoes_produto(id) ON DELETE CASCADE
);
