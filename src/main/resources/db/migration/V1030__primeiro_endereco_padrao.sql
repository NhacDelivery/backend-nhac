-- Contas antigas podiam ter apenas endereços sem padrão, inclusive o salvo pelo GPS.
UPDATE tb_enderecos_usuario e
JOIN (
    SELECT usuario_id, MIN(id) AS id
    FROM tb_enderecos_usuario
    GROUP BY usuario_id
    HAVING MAX(is_padrao) = 0
) escolha ON escolha.id = e.id AND escolha.usuario_id = e.usuario_id
SET e.is_padrao = TRUE;
