CREATE TABLE tb_repasses_entregador (
 pedido_id VARCHAR(50) PRIMARY KEY, entregador_id VARCHAR(50) NOT NULL,
 valor_devido DECIMAL(12,2) NOT NULL, valor_pago DECIMAL(12,2) NOT NULL DEFAULT 0,
 apurado_em TIMESTAMP(6) NOT NULL, pago_em TIMESTAMP(6), referencia_pagamento VARCHAR(100) UNIQUE,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_repasse_pedido FOREIGN KEY(pedido_id) REFERENCES tb_pedidos(id),
 CONSTRAINT fk_repasse_entregador FOREIGN KEY(entregador_id) REFERENCES tb_entregadores(id)
);
