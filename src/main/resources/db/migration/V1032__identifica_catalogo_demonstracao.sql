ALTER TABLE tb_produtos
    ADD COLUMN catalogo_demonstracao BOOLEAN NOT NULL DEFAULT FALSE;

-- A V998 criou 15 itens numerados para cada uma das lojas 0001 a 1000.
UPDATE tb_produtos p
JOIN tb_lojas l ON l.id = p.loja_id
SET p.catalogo_demonstracao = TRUE
WHERE l.id REGEXP '^loja_[0-9]{4}$'
  AND l.id BETWEEN 'loja_0001' AND 'loja_1000'
  AND l.usuario_id = CONCAT('user_lojista_', RIGHT(l.id, 4))
  AND RIGHT(p.id, 2) REGEXP '^(0[1-9]|1[0-5])$'
  AND p.id = CONCAT('prod_', l.id, '_', RIGHT(p.id, 2));
