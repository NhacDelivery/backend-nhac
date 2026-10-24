-- Pedidos existentes preservam o algoritmo anterior durante replays.
ALTER TABLE tb_pedidos ADD COLUMN idempotency_fingerprint_versao INT NOT NULL DEFAULT 1;
