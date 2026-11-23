ALTER TABLE tb_cupons ADD COLUMN loja_id VARCHAR(50), ADD COLUMN inicio DATETIME;
ALTER TABLE tb_cupons ADD CONSTRAINT fk_cupom_loja FOREIGN KEY (loja_id) REFERENCES tb_lojas(id);
CREATE INDEX idx_pedido_uso_cupom ON tb_pedidos (cupom_id, usuario_id, status);
