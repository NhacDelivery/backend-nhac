ALTER TABLE tb_pedidos
  ADD COLUMN codigo_entrega VARCHAR(8) NULL,
  ADD COLUMN codigo_entrega_tentativas INT NOT NULL DEFAULT 0,
  ADD COLUMN codigo_entrega_bloqueado_ate TIMESTAMP NULL;

-- Pedidos já em rota no momento do deploy
UPDATE tb_pedidos
SET codigo_entrega = LPAD(FLOOR(RAND() * 10000), 4, '0')
WHERE status = 'SAIU_ENTREGA' AND codigo_entrega IS NULL;
