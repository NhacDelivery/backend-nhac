# Nhac Backend
API de delivery que atende o app de clientes, o app de entregadores e o painel do lojista. Implementada em **Java 25, Spring Boot 4.1, Spring Security, JPA/Hibernate, MariaDB e Flyway**.

O projeto é um monólito organizado por funcionalidades, com controllers, serviços, entidades, repositórios e DTOs. Comece pela [explicação da arquitetura](docs/arquitetura.md) para entender o código antes de alterá-lo.

## Documentação
- [Índice](docs/README.md): localização de cada guia.
- [Arquitetura e organização](docs/arquitetura.md): módulos, fluxos e responsabilidades.
- [Cadastro e autenticação](docs/autenticacao.md): OTP, prova temporária, JWT e limite de requisições.
- [Permissões do lojista](docs/PERMISSOES-LOJISTA.md).
- [Pagamentos e conciliação](docs/operacao-pagamentos.md).
- [Desempenho](docs/performance.md) e [cache](docs/cache-caffeine.md).
- [Como contribuir e testar](docs/contribuir.md).
- Swagger local: `http://localhost:8080/swagger-ui/index.html`; especificação: `/v3/api-docs`.

## Executar localmente
Requisitos: JDK 25, Git e MariaDB 11.x local. O Maven Wrapper do repositório baixa o Maven; não precisa instalar uma segunda cópia. Docker é necessário para os testes MariaDB/Testcontainers, mas não para subir a API apontando para um MariaDB já instalado.

1. Clone o projeto:
```bash
git clone https://github.com/NhacDelivery/backend-nhac.git
cd backend-nhac
java -version
cp .env.example .env
```
2. Crie um banco e usuário exclusivos de desenvolvimento no MariaDB. Defina `DB_URL`, `DB_USER` e `DB_PASS` no `.env`. Não use o banco de produção.
3. Exporte as variáveis e execute:
```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```
No Windows, use `mvnw.cmd` e configure as variáveis no PowerShell ou no `envFile` do VS Code. Spring Boot **não lê um arquivo .env automaticamente**.

O perfil `dev` é o padrão. Flyway prepara o banco, incluindo o catálogo de demonstração histórico da migration V998. `DataLoader` também contém fixtures de desenvolvimento. A configuração de exemplo simula e-mail e upload; pagamentos/Google exigem configurar seus sandboxes para validar fluxos reais. Não publique senhas de desenvolvimento.

## Perfis
| Perfil | Finalidade |
| --- | --- |
| `dev` | Desenvolvimento com MariaDB; mocks explícitos e fixtures locais. |
| `test` | H2 e mocks usados pelos testes. Não representa todas as regras de MariaDB. |
| `e2e` | MariaDB isolado, fixtures e provedores simulados. **O loader apaga e recria os dados da fixture.** |
| `prod` | Exige secrets externos e valida migrations. Simulação de pagamento indisponível. |

Em produção configure `SPRING_PROFILES_ACTIVE=prod`. Consulte `.env.example` e `application-prod.properties`; este último é a referência de variáveis obrigatórias. Não versione `.env`, credenciais Firebase ou tokens.

## Testar
```bash
./mvnw test
./mvnw verify
```
`test` executa os testes comuns; `verify` acrescenta os `*IT`, a aplicação de migrations em MariaDB limpo e a verificação de cobertura. A suíte completa exige Docker. Testes marcados `manual` precisam de configuração externa e são excluídos da execução normal de integração.

Os detalhes do E2E Android estão em [e2e-ci](docs/e2e-ci.md). Os testes não autorizam executar pedidos, cobranças ou carga de concorrência em produção.

## Contratos principais
Todas as rotas abaixo usam o prefixo `/api/v1`.
| Área | Exemplos e regras |
| --- | --- |
| Autenticação | `POST /auth/login`, `/auth/social`, `/auth/login-sms`. Cadastro explicado no guia de autenticação. |
| Lojas | `GET /lojas` lista abertas; `GET /lojas/{id}` também permite consultar fechadas. `GET /lojas/minha-loja` exige autenticação. |
| Cadastro de loja | `POST /lojas` vincula ao usuário autenticado; o ID do dono enviado no corpo não concede acesso. |
| Frete | `POST /lojas/{id}/calcular-frete` usa a taxa base configurada. Atualmente não calcula preço por distância. |
| Produtos | Catálogo público paginado; alteração exige papel, cargo e vínculo com a loja. `preco` é o preço final; não reaplicar `percentualDesconto`. |
| Pedidos | `POST /pedidos` reserva estoque e calcula preços no servidor. `GET /pedidos/ativos` retorna todos os ativos. |
| Idempotência | `Idempotency-Key` + mesma intenção retorna o pedido existente com HTTP 200. Dados diferentes retornam conflito. Sem replay, criação usa 201. |
| Pagamentos | `GET /pedidos/{id}/pagamento` retoma o pagamento; confirmação usa webhook autenticado. Falha inconclusiva preserva a reserva. |
| Cancelamento | Cliente pode cancelar PENDENTE. Para pedido PAGO antes de preparo, há `POST /pedidos/{id}/estorno`. |
| Entregas | Ofertas, aceite, coleta, localização e conclusão por código; um entregador não assume duas corridas ativas. |
| Social | Feed, comentários, curtidas, salvos e chat com autorização de participantes. |

Estados do pedido: `PENDENTE → PAGO → PREPARANDO → SAIU_ENTREGA → ENTREGUE`. Os cancelamentos dependem da fase e da operação autorizada. Pedidos distintos de um cliente podem coexistir.

A troca de status do lojista não permite concluir a entrega; a conclusão passa pelo entregador e código. Os detalhes de cada endpoint estão nos controllers/DTOs e no Swagger, inclusive os contratos adicionais de feed e painel.

## Erros
Erros tratados usam `ErroPadraoDTO`, com `requestId`, `timestamp`, `status`, `errorCode`, `title`, `message`, `details`, `path` e `sugestoes`. Exemplos de códigos: `VALIDACAO_FALHOU`, `ACESSO_NEGADO`, `IDEMPOTENCIA_CONFLITO`, `PAGAMENTO_INDISPONIVEL` e `TENTATIVAS_LOGIN_EXCEDIDAS`. Verifique os nomes exatos no enum `ErrorCode`.

Uma falha ao criar pagamento pode incluir `details.pedidoId`: o pedido já foi reservado, então o cliente deve retomar sua consulta, preservando a chave de idempotência.

## Operação
Caffeine e broker WebSocket são locais à instância. Escalar réplicas exige coordenar rate limiting e entrega de eventos. Jobs `@Scheduled` dependem de instância ativa e CPU disponível; consulte o guia de pagamentos antes de configurar Cloud Run.

Benchmarks antigos estão em [docs/historico](docs/historico/README.md), identificados como evidência histórica. Eles não são uma promessa de latência da versão atual.
