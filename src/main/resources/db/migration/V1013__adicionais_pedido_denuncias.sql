CREATE TABLE tb_item_pedido_adicionais (
    item_pedido_id VARCHAR(50) NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    CONSTRAINT fk_item_pedido_adicionais FOREIGN KEY (item_pedido_id) REFERENCES tb_itens_pedido(id) ON DELETE CASCADE
);
CREATE TABLE tb_feed_denuncias (
    id VARCHAR(50) PRIMARY KEY,
    usuario_id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50) NOT NULL,
    comentario_id VARCHAR(50),
    motivo VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL
);
