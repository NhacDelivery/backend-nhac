-- Telefone é um identificador de login: duplicatas existentes precisam ser
-- resolvidas explicitamente antes desta migration; nunca escolher uma conta.
CREATE UNIQUE INDEX uk_usuarios_telefone ON tb_usuarios (telefone);
