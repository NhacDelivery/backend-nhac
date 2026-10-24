-- Provas antigas baseadas apenas em utilizado=true não são confiáveis.
-- As colunas novas ficam nulas: um novo cadastro deve confirmar seu OTP.
ALTER TABLE tb_codigos_verificacao_email
    ADD COLUMN validado_em DATETIME(6) NULL,
    ADD COLUMN consumido_em DATETIME(6) NULL,
    ADD COLUMN token_cadastro_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_codigo_email_token_cadastro ON tb_codigos_verificacao_email(token_cadastro_hash);
