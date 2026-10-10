# Permissões do painel lojista

`LojaAccessService` valida cargos sobre o usuário carregado do banco por requisição; alterar o cargo não exige renovar token. Os controles de propriedade/vínculo continuam nos serviços.

| Área | LOJISTA / ADMIN | FUNCIONARIO Administrador | Gerente | Atendente / desconhecido |
| --- | --- | --- | --- | --- |
| Pedidos/chat | Sim | Sim | Sim | Sim |
| Catálogo/painel/abertura | Sim | Sim | Sim | Não |
| Financeiro | Sim | Sim | Não | Não |
| Gestão da equipe e configuração da loja | Sim | Não | Não | Não |

Um cargo Administrador não equivale ao papel global ADMIN. Funcionário com cargo ausente/desconhecido recebe os acessos de Atendente, sem acesso ao catálogo ou financeiro. A criação de produtos aceita `isAtivo` (alias legado `ativo`); omissão mantém o padrão ativo, e `false` é persistido na mesma transação de criação.

Verificação: `PermissoesCargoLojistaIT` cobre chamadas HTTP, estoque sem mutação em 403, rebaixamento de cargo com token emitido antes da alteração, criação inativa e compatibilidade com payload antigo.
