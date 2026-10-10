# Telefones legados e V1015

A migração verifica colisões antes de alterar os telefones. Se houver números reais que coincidam após normalização, ela interrompe com diagnóstico explícito. Não apaga usuários, não desativa contas e não escolhe quem é dono do telefone.

Antes do deploy, execute a consulta de diagnóstico com acesso restrito ao banco:

```sql
SELECT telefone_canonico, COUNT(*) AS contas, GROUP_CONCAT(id ORDER BY id) AS ids
FROM (
    SELECT id, CASE
        WHEN telefone IS NULL OR TRIM(telefone) = '' OR REGEXP_REPLACE(telefone, '[^0-9]', '') = '00000000000' THEN NULL
        WHEN telefone NOT LIKE '+%' AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (10, 11)
            THEN CONCAT('+55', REGEXP_REPLACE(telefone, '[^0-9]', ''))
        WHEN REGEXP_REPLACE(telefone, '[^0-9]', '') LIKE '55%' AND LENGTH(REGEXP_REPLACE(telefone, '[^0-9]', '')) IN (12, 13)
            THEN CONCAT('+', REGEXP_REPLACE(telefone, '[^0-9]', ''))
        ELSE telefone
    END AS telefone_canonico FROM tb_usuarios
) normalizados
WHERE telefone_canonico IS NOT NULL
GROUP BY telefone_canonico HAVING COUNT(*) > 1;
```

Resolva cada colisão verificando a posse do número. Só remova o vínculo de uma conta após essa verificação, mantendo o histórico da decisão. Depois, execute novamente o diagnóstico e retome a migração. Se o Flyway registrar uma migration falhada no MariaDB, confira o estado do schema antes de executar `repair` com o mesmo artefato. A checagem acontece antes das alterações de dados; a procedure temporária é removida/recriada na próxima tentativa.
