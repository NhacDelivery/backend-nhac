ALTER TABLE tb_pedidos ADD COLUMN pagamento_expira_em TIMESTAMP NULL;
UPDATE tb_pedidos
SET pagamento_expira_em = DATE_ADD(criado_em, INTERVAL 7 MINUTE)
WHERE status = 'PENDENTE' AND UPPER(forma_pagamento) IN ('PIX', 'CARTAO', 'STRIPE', 'GOOGLE_PAY')
  AND criado_em IS NOT NULL;
CREATE INDEX idx_pedido_pagamento_expiracao ON tb_pedidos (status, pagamento_expira_em);
