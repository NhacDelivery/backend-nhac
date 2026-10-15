-- Recupera somente donos identificados pela relação canônica, com uma única loja.
-- CPF ausente não pode ser inferido. Funcionários e entregadores não são alterados.
UPDATE tb_usuarios u
JOIN (SELECT usuario_id, MIN(id) AS loja_id FROM tb_lojas
      WHERE usuario_id IS NOT NULL GROUP BY usuario_id HAVING COUNT(*) = 1) l
  ON l.usuario_id = u.id
SET u.loja_vinculada_id = l.loja_id,
    u.cargo = 'ADMINISTRADOR',
    u.papel = CASE WHEN u.papel = 'CLIENTE' THEN 'LOJISTA' ELSE u.papel END
WHERE u.papel IN ('CLIENTE', 'LOJISTA', 'ADMIN')
  AND (u.loja_vinculada_id IS NULL OR u.loja_vinculada_id = l.loja_id);
