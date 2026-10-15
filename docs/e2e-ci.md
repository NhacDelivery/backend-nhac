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
`e2e`; não acessa Render nem confirma pagamentos reais. O runner do cliente acrescenta entrega completa e retomada de PIX/cartão:
loja e entregador usam APIs autenticadas, e o cliente valida sua interface real.
O entregador é cadastrado durante o cenário usando a conta secundária existente;
nenhuma rota especial de alteração de status ou reset foi adicionada.

O teste de cartão envia eventos assinados ao webhook, exige efeito PAGO no banco
e cobre assinatura inválida, falha recuperável, repetição e evento de falha
posterior ao sucesso. Os testes de controller também usam HMAC real e a versão
de API do SDK, sem mock estático da verificação de assinatura.

Asserções de entrega incluem código errado/correto, retries de coleta/conclusão,
cliente sem pedidos ativos, entregador ONLINE, histórico e ganhos únicos,
estoque reservado uma vez e avaliação do cliente persistida. PIX retoma a mesma
cobrança/código/prazo e confirma com a simulação existente no perfil E2E.

Não há homologação do PaymentSheet ou dos provedores externos, UI de loja ou
motoboy, expiração ou reinício real de processo. A retomada abre uma tela nova
pelo histórico dentro do mesmo cenário. Consulte a matriz e os próximos casos
em `Nhac/docs/e2e-ci.md`. Integrar o cliente #34 primeiro disponibiliza estes
seis alvos para o workflow do backend #150.
