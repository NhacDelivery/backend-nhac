# Como o backend Nhac funciona

## Visão geral
O backend é um programa Java que recebe pedidos HTTP dos três aplicativos, verifica quem está chamando, executa regras de negócio e persiste dados no MariaDB. Ele também conversa com provedores externos e envia eventos em tempo real.

É um **monólito organizado por funcionalidades**: todo o código sobe no mesmo processo e usa o mesmo banco. A organização tem camadas, mas não é Clean Architecture estrita: entidades usam anotações JPA, serviços usam Spring e DTOs ainda possuem conversão para entidades. Isso é aceitável para o TCC; não é preciso criar microsserviços para corrigir bugs.

## Pastas e responsabilidades
| Caminho | Responsabilidade | Quando mexer |
| --- | --- | --- |
| `BackendNhacApplication` | Inicializa Spring Boot, agenda tarefas, configura UTC e cliente HTTP. | Configuração global de execução. |
| `config/` | CORS auxiliar, loaders e cache Caffeine. | Ambiente de desenvolvimento e cache. |
| `domain/` | Funcionalidades e regras do sistema. | Mudança de comportamento do produto. |
| `infra/security/` | JWT, filtro HTTP, papéis e limite de autenticação. | Autenticação e autorização de entrada. |
| `infra/websocket/` | Conexão STOMP, publicação permitida e autorização das assinaturas. | Chat e eventos ao vivo. |
| `infra/email/` | Cliente HTTP da Brevo. | Integração de e-mail. |
| `infra/storage/` | Upload no Firebase Storage e configuração de credenciais. | Armazenamento das imagens. |
| `exceptions/` | Exceções de negócio, códigos e resposta HTTP padrão. | Erros que o aplicativo precisa entender. |
| `resources/application*.properties` | Configuração por perfil e variáveis de ambiente. | Banco, mocks, limites e serviços externos. |
| `resources/db/migration/` | Histórico versionado de alterações de banco. | Criar nova migration, sem editar as já aplicadas. |
| `src/test/` | Testes unitários, HTTP, banco, segurança e concorrência. | Provar o comportamento desejado e evitar regressões. |
| `tools/` | Scripts de medição e geração da base de laboratório. | Investigar desempenho de forma reproduzível. |
| `docs/` | Contratos atuais, operação e explicações; histórico separado. | Atualizar junto com as mudanças. |

## Dentro de uma funcionalidade
- **Controller:** recebe URL, corpo e usuário autenticado; valida a entrada e devolve HTTP/DTO. Evite consultar banco e executar regra complexa diretamente nele.
- **Service:** decide se a operação pode acontecer e coordena persistência, transações e integrações.
- **Repository:** declara as consultas e comandos executados no banco. O Spring Data gera boa parte da implementação.
- **Entity:** representa um registro e suas relações. Exemplo: Pedido contém itens, loja, estado e identificação de pagamento.
- **DTO:** define o que entra e sai da API. Não é necessário devolver a entidade inteira ao aplicativo.
- **Event/listener:** comunica que algo aconteceu. O evento de status de pedido é emitido após o commit para não anunciar algo que foi revertido.

Uma chamada comum percorre filtro de segurança, controller, service e repository. O filtro consulta o usuário atual, por isso desativação e mudança de cargo podem refletir nas próximas requisições mesmo com JWT já emitido.

## Módulos de negócio
| Pacote | O que faz |
| --- | --- |
| `auth` | Login por senha/Google/SMS, OTP de e-mail, prova temporária de cadastro e recuperação de senha. |
| `usuario` | Perfil, endereços, telefone, preferências e funcionários. |
| `loja` | Cadastro da loja, endereço, abertura, acesso da equipe e regra de frete. |
| `produto` | Cardápio, estoque, categorias e grupos de adicionais. |
| `pedido` | Checkout, histórico, estados, pagamentos Stripe/Asaas, conciliação e estorno. |
| `cupom` | Resgate, desconto, consumo e devolução. |
| `entrega` | Ofertas, despacho, aceite, coleta, rota, código de conclusão e suporte. |
| `entregador` | Cadastro do motoboy, veículo, disponibilidade, GPS, ganhos e registros de repasse. |
| `lojista`, `painel`, `financeiro` | Visões operacionais para a loja: pedidos, produtos, indicadores e agregações. |
| `chat` | Conversas loja/cliente/entregador e conversas diretas entre clientes. |
| `feed` | Publicações, comentários, respostas, curtidas, salvos e denúncias. |
| `favorito` | Relação de acompanhamento de lojas. |
| `avaliacao`, `avaliacao_produto`, `avaliacao_entregador` | Avaliações dos três objetos, com regras e agregados diferentes. |
| `notificacao` | Preferências, envio push e avisos persistentes para o entregador. |
| `upload` | Validação e envio de imagens ao storage. |

## Um checkout, por dentro
1. O controller recebe o carrinho e a Idempotency-Key.
2. PedidoReservaService abre uma transação curta, bloqueia o usuário, verifica replay e valida loja/produtos/adicionais.
3. Os preços vêm do banco. A reserva de estoque usa UPDATE condicional; cupom e itens ficam na mesma transação.
4. O pedido é salvo antes de chamar o gateway. A transação é encerrada.
5. PedidoService escolhe PIX/Asaas, cartão/Stripe ou dinheiro. O gateway recebe um snapshot, não uma entidade gerenciada.
6. O identificador externo é vinculado por atualização específica. Timeout mantém o estado incerto para conciliação.
7. O aplicativo retoma a consulta do mesmo pedido. O webhook confirma PAGO, depois a loja pode iniciar o preparo.
8. O despacho oferece a corrida. O banco resolve o aceite concorrente. Aceitar não significa coletar.
9. Após coleta, o status vira SAIU_ENTREGA; a conclusão exige código e registra a entrega.

O frete atual é a taxa base da loja; coordenadas são necessárias para o fluxo de entrega, mas não tornam o cálculo do preço automaticamente geográfico.

## Cancelamento e estoque
Cancelar pedido pendente usa uma transação curta para autorizar e capturar o snapshot. A consulta/cancelamento no gateway acontece sem lock ou conexão de banco ocupada. Outra transação bloqueia novamente o pedido, confere se ele ainda é compatível e efetiva cancelamento/devolução.

Se o processo falhar, a reserva persistida permite retry ou conciliação. Um pagamento inconclusivo não autoriza liberar estoque. O incremento de estoque é atômico no banco; cancelar dois pedidos do mesmo produto não pode perder unidades.

Estorno de pedido já pago usa fila persistida e transição condicional antes do POST externo. Status CANCELADO não significa que o dinheiro já foi devolvido.

## Autenticação e permissões
O servidor gera a identidade da conta. O OTP confirmado gera um token de cadastro temporário, armazenado como hash, vinculado ao e-mail e consumido uma vez.

Papel principal, vínculo com loja e cargo são coisas diferentes:
- CLIENTE pode usar o app e pode ter um cadastro adicional de entregador.
- O vínculo ativo de entregador acrescenta ROLE_ENTREGADOR.
- LOJISTA possui a loja; FUNCIONARIO possui vínculo e cargo nela.
- Cargo Administrador da loja não é o papel global ADMIN.
- Operações administrativas de suporte exigem ADMIN ativo sem vínculo de loja.

O WebSocket autentica CONNECT e autoriza SUBSCRIBE; SEND só pode ir para as rotas permitidas da aplicação. Tópicos do servidor não são uma API de escrita do cliente.

## Banco, cache e tempo
Flyway aplica migrations em ordem e registra checksum. Alterar uma migration já aplicada gera divergência entre ambientes. Por isso correções novas entram em V1024/V1025, preservando as anteriores.

Caffeine guarda DTOs públicos por prazo limitado; não guarda permissões ou pagamentos. O cache é local à JVM. MariaDB continua sendo a fonte das regras de estoque e dinheiro.

O armazenamento usa UTC e instantes absolutos. A configuração JDBC usa UTC e cada conexão MariaDB executa SET time_zone para +00:00; o perfil H2 usa o comando equivalente. Isso também mantém consistentes as funções de data usadas nas agregações. Financeiro apresenta datas/horas de São Paulo e mantém a regra atual de contar pedidos não cancelados, inclusive pendentes. Isso é volume comercial, não prova de recebimento de dinheiro.

## Arquivos que parecem aleatórios, mas têm função
| Arquivo/pasta | Por que existe |
| --- | --- |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Executam a versão configurada do Maven em Linux/Windows e no CI. |
| `Dockerfile`, `.dockerignore` | Empacotam a API para hospedagem e controlam o contexto do build. |
| `.github/workflows/` | Compilação, testes, cobertura, scans e E2E. |
| `.github/scripts/` | Orquestra o runner E2E do aplicativo cliente. |
| `qodana.yaml` | Configura a análise estática já integrada. |
| `nhac-logo.png` | A URL do arquivo é usada nos e-mails. |
| `.vscode/` | Configuração de execução, ambiente e formatação para a equipe. É opcional para quem usa outra IDE. |
| `GeradorCatalogoInsano` | Gera SQL de demonstração; possui testes e procedimento próprio. O nome é informal, mas a função existe. |
| `docs/historico/` | Guarda evidências antigas sem apresentá-las como contrato vigente. |
| `V998__populando_catalogo_completo.sql` | Migration histórica de demonstração. Não deve ser apagada de um banco que já a aplicou. |

O HELP.md genérico do Spring Initializr foi removido. A dependência jjwt-api foi removida porque a implementação usa java-jwt da Auth0. A exceção PedidoAtivoException e consultas de confirmação antigas deixaram de existir porque não eram usadas e representavam regras ultrapassadas.

## Dívidas técnicas que ainda fazem sentido estudar
- Separar melhor regras puras, DTOs e entidades; alguns DTOs ainda conhecem repositórios.
- Atualização absoluta de estoque pelo lojista ainda precisa de política explícita para concorrer com vendas.
- Revisar revogação de sessões WebSocket já abertas quando uma conta/cargo muda.
- Coordenar rate limiting e eventos ao escalar réplicas. O limite local é uma defesa adicional, não um limite distribuído.
- Extrair fixtures históricas para uma estratégia apropriada antes de transformar o TCC em produção real; não remover V998 indiscriminadamente.
- Registrar categoria histórica dos itens se os relatórios precisarem preservar a categoria da venda.
- Outbox e entrega durável de eventos são evolução futura; o broker simples não garante replay após queda.

## Por onde estudar
Leia Usuario, UsuarioRepository e um endpoint simples do perfil. Depois acompanhe CadastroService, PedidoReservaService, PedidoService, StatusPedido e os testes equivalentes. Só depois entre no despacho concorrente, gateways e WebSocket. Para cada regra, encontre o teste que prova sucesso, limite e falha.
