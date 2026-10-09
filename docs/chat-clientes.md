# Chat: conversas e mensagens

Todas as rotas REST exigem `Authorization: Bearer <JWT>`. Nas requisições com corpo, use `Content-Type: application/json`. O usuário remetente vem do token, nunca do JSON.

## Rotas do cliente

| Método | Rota | Corpo JSON | Finalidade |
| --- | --- | --- | --- |
| GET | `/api/v1/conversas?page=0&size=20` | Nenhum | Lista as conversas do cliente com lojas e outros clientes. |
| POST | `/api/v1/conversas/clientes/{clienteId}` | Nenhum | Abre ou recupera o chat com outro cliente ativo. |
| POST | `/api/v1/conversas/lojas/{lojaId}` | Nenhum | Abre ou recupera o chat com uma loja (rota já existente). |
| POST | `/api/v1/conversas/{conversaId}/mensagens` | `EnviarMensagemDTO` abaixo | Envia mensagem para loja ou outro cliente e publica no tópico WebSocket. |
| GET | `/api/v1/conversas/{conversaId}/mensagens?page=0&size=30` | Nenhum | Histórico, mais recentes primeiro. |
| PATCH | `/api/v1/conversas/{conversaId}/lida` | Nenhum | Zera somente as não lidas do cliente autenticado; retorna 204. |

A abertura retorna 200 com `{"id":"conv_..."}`. O mesmo par de clientes retorna o mesmo ID nos dois sentidos. Apenas usuários com papel CLIENTE e conta ativa podem usar o chat direto; terceiros, lojas e administradores não podem acessar suas mensagens. Conversar consigo mesmo é recusado.

Listagem de conversas: máximo de 100 itens por página, ordenação fixa por última atividade decrescente e ID para desempate. Inclui conversas ainda sem mensagem. `tipo` é `LOJA` ou `CLIENTE`; `interlocutor` identifica a loja ou o outro cliente. Não expõe email, telefone, senha ou demais dados da conta.

Exemplo resumido de resposta (outros campos de paginação do Spring também são retornados):

```json
{
  "content": [
    {
      "id": "conv_...",
      "tipo": "CLIENTE",
      "interlocutor": {"id": "uuid-do-outro-cliente", "nome": "Ana", "imagemUrl": null},
      "ultimaMensagemPreview": "Oi!",
      "ultimaMensagemEm": "2026-10-08T13:00:00Z",
      "naoLidas": 1
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### EnviarMensagemDTO

```json
{
  "conteudo": "Oi! Tudo bem?",
  "clientMessageId": "123e4567-e89b-12d3-a456-426614174000"
}
```

`conteudo` é obrigatório, não pode conter apenas espaços e tem limite de 4000 caracteres. `clientMessageId` é opcional e deve ser UUID: gere um novo para cada mensagem e preserve-o ao repetir a mesma tentativa após falha de conexão. Repetir o mesmo ID com a mesma conversa, remetente e texto retorna a mensagem já salva, sem duplicar persistência ou não lidas. Reutilizar o ID para outro envio retorna 403. O tópico pode transmitir novamente o mesmo evento; o consumidor deve deduplicar pelo `id`.

Resposta 200:

```json
{
  "id": "msg_123e4567-e89b-12d3-a456-426614174000",
  "conversaId": "conv_...",
  "remetenteTipo": "CLIENTE",
  "remetenteUsuarioId": "uuid-do-cliente-autenticado",
  "conteudo": "Oi! Tudo bem?",
  "enviadaEm": "2026-10-08T13:00:00Z"
}
```

O histórico retorna uma página cujo `content` contém mensagens nesse formato. Nos chats diretos ambos os remetentes têm tipo CLIENTE; use `remetenteUsuarioId` para distinguir quem enviou.

## Outras caixas de conversas

- `GET /api/v1/entregador/conversas?page=0&size=20`: lista chats com lojas do perfil de entregador ativo. Mesmo formato da caixa do cliente, com `tipo: "LOJA"`. Exige autoridade ENTREGADOR ou ADMIN e o vínculo real de entregador.
- `GET /api/v1/lojista/conversas?page=0&size=20&tipo=CLIENTE`: rota existente da loja; `tipo` é opcional (CLIENTE ou ENTREGADOR). Seu formato existente foi preservado. Não inclui chats diretos entre clientes.

## WebSocket

Conectar em `/ws-native` (WebSocket nativo) ou `/ws` (SockJS), usando STOMP:

1. CONNECT: header `Authorization: Bearer <JWT>`.
2. SUBSCRIBE em `/topic/conversas/{conversaId}` para mensagens.
3. SEND em `/app/conversas/{conversaId}/enviar` com o mesmo JSON de EnviarMensagemDTO.
4. SUBSCRIBE em `/user/queue/erros` para erros de envio (`{"erro":"descrição"}`).

REST e STOMP usam o mesmo armazenamento, permissões e identificadores. O ChatService agenda um evento imutável e ChatMensagemPublisher transmite somente em AFTER_COMMIT da transação externa; rollback ou falha no commit não publicam. Falha do broker após commit é registrada no log e não altera a persistência já confirmada; o histórico REST permite recuperar a mensagem. Apenas os dois clientes podem assinar ou enviar no tópico de um chat direto. Enviar por REST também transmite aos assinantes depois da transação de persistência.

## Erros e persistência

- 400: mensagem inválida, autochat, destinatário sem papel CLIENTE ou inativo.
- 401/403: token ausente/inválido; 403: usuário não autorizado ou ID de mensagem reutilizado para outro conteúdo/remetente/conversa.
- 404: cliente ou conversa inexistente.
- 409: conflito de concorrência, conforme tratamento global do backend.

V1014 mantém todas as conversas e mensagens anteriores. `loja_id` pode ser nulo somente em chats diretos, que possuem `segundo_cliente_id`. O serviço ordena os IDs e bloqueia os usuários nessa ordem ao abrir um chat, evitando criação duplicada em requisições simultâneas. Constraint de unicidade protege o par persistido. Mensagens e leitura do cliente bloqueiam a conversa durante a alteração para preservar contadores sob concorrência. Os campos antigos de não lidas são preservados: no chat direto, `nao_lidas_cliente` pertence ao primeiro ID e `nao_lidas_loja` ao segundo; a API sempre devolve `naoLidas` da perspectiva autenticada.

Validação: ChatClientesIT (REST, autorização, idempotência, concorrência, paginação), ChatFlowIT (regressões loja/entregador), ChatWebSocketIT (STOMP nos dois sentidos, broadcast REST e recusa de terceiros) e ChatClientesMigrationIT (upgrade real de MariaDB com preservação e constraints). Rodar `./mvnw -B -ntp verify` com JDK 25 e Docker disponível.

O controller REST do cliente exige ROLE_CLIENTE por @PreAuthorize, além das validações de domínio. O EntityGraph carrega as lojas junto da página, verificado pelo teste de inicialização da associação. ChatMensagemCommitIT cobre commit externo, rollback e falha do banco; ChatMensagemPublisherTest cobre falha do broker após commit.

O runner E2E do backend prepara somente o helper de toque do checkout temporário do app, centralizando o alvo antes de validar hit-test e tocar. Todos os cenários e as três repetições continuam obrigatórios.
