CREATE TABLE tb_avaliacoes_entregador (
    id VARCHAR(50) NOT NULL,
    pedido_id VARCHAR(50) NOT NULL,
    entregador_id VARCHAR(50) NOT NULL,
    usuario_id VARCHAR(50) NOT NULL,
    nota TINYINT NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario TEXT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_avaliacao_entregador_pedido (pedido_id),
    INDEX idx_avaliacao_entregador_criado (entregador_id, criado_em),
    CONSTRAINT fk_avaliacao_entregador_pedido FOREIGN KEY (pedido_id) REFERENCES tb_pedidos(id),
    CONSTRAINT fk_avaliacao_entregador_entregador FOREIGN KEY (entregador_id) REFERENCES tb_entregadores(id),
    CONSTRAINT fk_avaliacao_entregador_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id)
);
