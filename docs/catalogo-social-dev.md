# Catálogo social de demonstração

Use somente em um banco MariaDB de desenvolvimento já migrado até V1009.
O script cria conteúdo demonstrativo persistido, explicitamente identificado como
“Demonstração”. Não é fallback do app nem uma migration automática de produção.

```bash
# Defina NHAC_SEED_SENHA_HASH com um bcrypt da senha desejada para as contas demo.
java src/main/java/br/com/nhac/backend_nhac/util/GeradorCatalogoInsano.java --lojas-existentes
mariadb -h localhost -u nhac -p nhac_dev < tools/catalogo_social_dev.sql
```

É possível passar outro caminho depois de `--lojas-existentes`.
O SQL preserva lojas, produtos e donos existentes. Cria um lojista por loja sem dono,
vincula a conta, cria três clientes de demonstração e três pedidos entregues de valor
zero por loja, necessários para a FK/unique de avaliações. Esses pedidos não acionam
pagamentos, despacho nem estoque e são identificados pela observação de demonstração.

Cada pedido recebe uma avaliação (notas 5, 4 e 5). Médias e totais são recalculados
pela tabela de avaliações, incluindo avaliações já existentes. Cada loja ganha um
post de cliente e, quando o dono é LOJISTA/ADMIN ativo, um post promocional. Imagem
HTTPS da loja é reutilizada; donos inativos e papéis incompatíveis não são alterados.
Contas novas usam o bcrypt informado; nunca troca senha ou papel de uma conta existente.

IDs são determinísticos. Executar novamente não duplica contas, posts, pedidos,
fotos, hashtags ou avaliações. Use uma cópia de DEV sem escritas concorrentes.
O script não modifica conteúdo já criado com esses IDs.

O modo antigo (sem `--lojas-existentes`) continua gerando o catálogo completo,
agora em `tools/catalogo_completo_dev.sql`, sem sobrescrever V998 ou outra migration.
Esse arquivo contém apenas o catálogo base; aplique o complemento social após
as migrations. Não reaplique o catálogo completo em um banco já populado.

Validação: `MariaDbMigrationIT` aplica o complemento duas vezes em MariaDB real,
verifica idempotência, vínculo do lojista novo, preservação de dono e médias reais.
