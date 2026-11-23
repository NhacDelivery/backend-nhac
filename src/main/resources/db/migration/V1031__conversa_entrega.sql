ALTER TABLE tb_conversas
    ADD COLUMN pedido_id VARCHAR(50) NULL,
    ADD COLUMN entregador_usuario_id VARCHAR(50) NULL,
    ADD CONSTRAINT fk_chat_entrega_pedido FOREIGN KEY (pedido_id) REFERENCES tb_pedidos(id),
    ADD CONSTRAINT fk_chat_entrega_usuario FOREIGN KEY (entregador_usuario_id) REFERENCES tb_usuarios(id),
    ADD CONSTRAINT uq_chat_entrega UNIQUE (pedido_id, entregador_usuario_id);

ALTER TABLE tb_conversas
    DROP CONSTRAINT ck_conversas_canal,
    ADD CONSTRAINT ck_chat_canal CHECK (
        (pedido_id IS NULL AND entregador_usuario_id IS NULL AND
            ((loja_id IS NOT NULL AND segundo_cliente_id IS NULL AND participante_tipo IN ('CLIENTE','ENTREGADOR'))
            OR (loja_id IS NULL AND segundo_cliente_id IS NOT NULL AND cliente_id <> segundo_cliente_id AND participante_tipo = 'CLIENTE')))
        OR (pedido_id IS NOT NULL AND entregador_usuario_id IS NOT NULL AND
            loja_id IS NULL AND segundo_cliente_id IS NULL AND cliente_id <> entregador_usuario_id AND participante_tipo = 'ENTREGA')
    );
