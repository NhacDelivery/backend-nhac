ALTER TABLE tb_pedidos ADD COLUMN estorno_status VARCHAR(20) NULL;
ALTER TABLE tb_pedidos ADD COLUMN estorno_atualizado_em TIMESTAMP(6) NULL;
CREATE INDEX idx_pedido_estorno_fila ON tb_pedidos(estorno_status, estorno_atualizado_em);
CREATE INDEX idx_pedido_pagamento_pendente ON tb_pedidos(status, pagamento_expira_em);
