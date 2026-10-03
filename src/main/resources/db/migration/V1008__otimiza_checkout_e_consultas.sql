ALTER TABLE tb_pedidos ADD COLUMN pagamento_criacao_incerta BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE tb_pagamento_clientes (id VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(80) NOT NULL);
CREATE INDEX idx_pedidos_usuario_criado ON tb_pedidos (usuario_id, criado_em);
CREATE INDEX idx_pedidos_usuario_status_criado ON tb_pedidos (usuario_id, status, criado_em);
CREATE INDEX idx_pedidos_loja_status_criado ON tb_pedidos (loja_id, status, criado_em);
CREATE INDEX idx_produtos_loja_ativo_id ON tb_produtos (loja_id, is_ativo, id);
