-- Contas Google sem telefone usam NULL, não um número fictício compartilhado.
ALTER TABLE tb_usuarios MODIFY COLUMN telefone VARCHAR(20) NULL;
UPDATE tb_usuarios SET telefone = NULL
WHERE telefone IS NULL OR TRIM(telefone) = '' OR REGEXP_REPLACE(telefone, '[^0-9]', '') = '00000000000';
-- Canonicaliza os formatos brasileiros legados antes da unicidade.
UPDATE tb_usuarios SET telefone = CONCAT('+55', REGEXP_REPLACE(telefone, '[^0-9]', ''))
WHERE telefone IS NOT NULL AND telefone NOT LIKE '+%' AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (10, 11);
UPDATE tb_usuarios SET telefone = CONCAT('+', REGEXP_REPLACE(telefone, '[^0-9]', ''))
WHERE telefone IS NOT NULL AND REGEXP_REPLACE(telefone, '[^0-9]', '') LIKE '55%'
AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (12, 13);
-- Duplicatas canonicalizadas precisam ser resolvidas explicitamente; nunca escolher uma conta.
CREATE UNIQUE INDEX uk_usuarios_telefone ON tb_usuarios (telefone);
