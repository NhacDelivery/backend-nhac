# E2E com o cliente canônico

O workflow usa Nhac `eu-e-tu` e a revisão do backend que está sendo validada.
Push em `main`, PRs que afetam contratos e execução manual disparam a validação.
As refs são resolvidas uma vez e os checkouts seguintes usam os SHAs imutáveis.
O par efetivamente testado fica no resumo e no artefato `e2e-metadata/revisions.json`.

`app_ref` e `backend_ref` permitem escolher um par explícito no workflow manual;
vazios usam `eu-e-tu` e o commit do backend selecionado para a execução. Para
validar PRs coordenados antes de integrar, selecionar as duas branches nesses
campos. Integrar primeiro o cliente para disponibilizar o runner/smoke novo na
branch canônica e depois este workflow do backend.

O wrapper `.github/scripts/run-client-e2e.sh` executa o runner versionado do Nhac
e captura logcat; não modifica o código nem os helpers do cliente. A configuração
do banco é exclusiva do job e usa a mesma senha temporária no serviço e no runner.
PR/push usam uma repetição; execução manual usa três. Cada arquivo/tentativa
reinicia o backend no perfil `e2e`, restaurando a fixture. O smoke e os detalhes
da orquestração estão documentados em `Nhac/docs/e2e-ci.md`.

E2E Android é uma validação isolada, com provedores externos simulados no perfil
`e2e`; não acessa Render nem confirma pagamentos reais. As novas jornadas de
PIX/cartão e entrega completa serão acrescentadas na próxima etapa.
