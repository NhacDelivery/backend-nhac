# Feed do Nhac

Todas as rotas exigem o JWT atual e usam `/api/v1/feed/posts` como base.
Não há seed fictício: o feed permanece vazio até a publicação de posts reais.

| Método | Sufixo | Função |
|---|---|---|
| GET | vazio | Posts paginados por categoria |
| GET | /{id} | Detalhes e estado das interações de quem está logado |
| POST | vazio | Criar post; autor obtido da sessão |
| PUT | /{id} | Editar post (autor ou ADMIN) |
| DELETE | /{id} | Excluir post e dependências (autor ou ADMIN) |
| PUT / DELETE | /{id}/curtida | Curtir/descurtir de forma idempotente |
| PUT / DELETE | /{id}/salvo | Salvar/remover de salvos de forma idempotente |
| GET | /salvos | Posts salvos de quem está logado |
| GET / POST | /{id}/comentarios | Listar/criar comentários |
| DELETE | /{id}/comentarios/{comentarioId} | Excluir comentário (seu autor, autor do post ou ADMIN) |

GET paginados recebem `page` (a partir de zero) e `size` (1–50, padrão 20).
Resposta usa `content`, `totalElements`, `totalPages`, `number`, `last` do Page existente no projeto.
`categoria`: `Destaques`, `Em Alta`, `Promoções`, `Novidades` (padrão `Destaques`).
Destaques e Novidades usam ordem cronológica decrescente; não há recomendação personalizada.
Em Alta ordena por curtidas, comentários, data e id. Promoções filtra posts patrocinados.
Comentários usam ordem cronológica crescente e desempate por id.

POST/PUT de post:

```json
{
  "conteudo": "Meu pedido chegou! #Nhac",
  "imagens": ["https://seu-storage/foto.jpg"],
  "hashTags": ["#Nhac"],
  "lojaId": null,
  "isPatrocinado": false,
  "sponsorLabel": null
}
```

Limites: conteúdo 5000 caracteres; 6 imagens HTTPS; 10 hashtags (60 caracteres);
comentário 2000 caracteres (`{"conteudo":"Gostei!"}`). Imagens são URLs; envio do arquivo
usa o serviço de upload existente e suas pastas permitidas. O servidor não busca essas URLs.
Só LOJISTA/FUNCIONARIO da loja referenciada ou ADMIN pode publicar promoção.
Clientes podem mencionar uma loja real sem declarar patrocínio.

A resposta mantém `id`, `nomeUsuario`, `avatarUrl`, `conteudo`, `imagens`, `hashTags`,
`curtidas`, `comentarios`, `isPatrocinado`, `sponsorLabel`, `mentionedStore` do app.
Acrescenta `usuarioId`, `salvos`, `curtido`, `salvo`, `criadoEm`, `atualizadoEm`.
`mentionedStore` inclui id/nome/imagem/avaliação reais da loja. Não expõe email/telefone.
Não fabrica badge, dispositivo ou topComment. Comentários retornam autor público,
conteúdo, data e `isAuthor` (autor do post).

Migração: `V1009__cria_feed.sql`. Contagens são atualizadas na mesma transação das
interações sob bloqueio pessimista do post. Uma unique composta impede duplicatas.
Leituras e escritas relacionadas a autores inativos não disponibilizam o post.
Alterações administrativas diretas no banco não são suportadas para contagens.

Validação: `./mvnw -Dtest=FeedIntegrationTest test`; `./mvnw verify` também inclui a
migração MariaDB existente. No ambiente do assistente, execução bloqueada por DNS
do Maven Central e JDK 17 (o projeto exige 25). CI deve validar antes do merge.

No Nhac, a listagem e os detalhes consomem a API, com comentários, curtidas e salvos reais.
A criação/edição de post está disponível pela API; o app atual ainda não possui composer.
O backend deve ser implantado antes da versão do app que remove os mocks.
