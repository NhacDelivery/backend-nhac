-- Interrompe antes de alterar dados caso números reais colidam após canonicalização.
-- A posse do número deve ser resolvida explicitamente; nunca escolher/apagar uma conta.
DROP PROCEDURE IF EXISTS nhac_validar_telefones_unicos;
CREATE PROCEDURE nhac_validar_telefones_unicos()
BEGIN
    IF EXISTS (
        SELECT telefone_canonico FROM (
            SELECT CASE
                WHEN telefone IS NULL OR TRIM(telefone) = '' OR REGEXP_REPLACE(telefone, '[^0-9]', '') = '00000000000' THEN NULL
                WHEN telefone NOT LIKE '+%' AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (10, 11)
                    THEN CONCAT('+55', REGEXP_REPLACE(telefone, '[^0-9]', ''))
                WHEN REGEXP_REPLACE(telefone, '[^0-9]', '') LIKE '55%' AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (12, 13)
                    THEN CONCAT('+', REGEXP_REPLACE(telefone, '[^0-9]', ''))
                ELSE telefone
            END AS telefone_canonico
            FROM tb_usuarios
        ) normalizados
        WHERE telefone_canonico IS NOT NULL
        GROUP BY telefone_canonico HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1015: telefones duplicados apos normalizacao. Resolver posse antes da migracao; consulte docs/telefone-unico.md';
    END IF;
END;
CALL nhac_validar_telefones_unicos();
DROP PROCEDURE nhac_validar_telefones_unicos;

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
