-- Preserva os canais loja/cliente e loja/entregador; chat direto não possui loja.
ALTER TABLE tb_conversas
    MODIFY COLUMN loja_id VARCHAR(50) NULL,
    ADD COLUMN segundo_cliente_id VARCHAR(50) NULL,
    ADD CONSTRAINT fk_conversas_segundo_cliente FOREIGN KEY (segundo_cliente_id) REFERENCES tb_usuarios(id),
    ADD CONSTRAINT uq_conversas_clientes UNIQUE (cliente_id, segundo_cliente_id),
    ADD CONSTRAINT ck_conversas_canal CHECK (
        (loja_id IS NOT NULL AND segundo_cliente_id IS NULL)
        OR (loja_id IS NULL AND segundo_cliente_id IS NOT NULL
            AND cliente_id <> segundo_cliente_id AND participante_tipo = 'CLIENTE')
    );

CREATE INDEX idx_conversas_cliente_recente ON tb_conversas (cliente_id, participante_tipo, ultima_mensagem_em);
CREATE INDEX idx_conversas_segundo_cliente_recente ON tb_conversas (segundo_cliente_id, ultima_mensagem_em);
