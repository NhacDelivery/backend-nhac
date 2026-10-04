# Cache de catálogo com Caffeine

Implementado a partir da `main` em `ea6984a2c44be405df54993db0c4daf82084290e`.
O cache guarda somente DTOs públicos, nunca entidades JPA, tokens ou permissões.

| Consulta | Cache | TTL padrão | Máximo de entradas |
| --- | --- | --- | --- |
| `GET /api/v1/lojas` sem coordenadas | `lojas` | 30 s | 200 páginas |
| `GET /api/v1/lojas/{id}` | `loja` | 30 s | 2.000 |
| `GET /api/v1/produtos` | `produtos` | 30 s | 200 páginas |
| `GET /api/v1/produtos/{produtoId}` | `produto` | 30 s | 2.000 |
| `GET /api/v1/produtos/{produtoId}/avaliacoes/resumo` | `produtoAvaliacoes` | 2 min | 2.000 |

Listagens só são cacheadas nas primeiras 20 páginas, com até 100 itens por página.
Páginas maiores, consultas sem paginação e busca de lojas por coordenadas continuam
funcionando diretamente no banco. Isso limita a retenção e evita guardar coordenadas
do cliente. Todos os argumentos participam da chave, incluindo filtros, tamanho,
página e ordenação; caches diferentes isolam métodos com assinaturas semelhantes.
Exceções não são cacheadas.

## Invalidação e consistência

- Criar uma loja limpa as páginas e os detalhes de lojas.
- Editar ou abrir/fechar uma loja limpa lojas e produtos: o DTO de produto contém
  também o nome e o estado de abertura da loja.
- Criar, editar, ativar ou desativar produtos limpa páginas e detalhes de produtos.
- Criar uma avaliação limpa lojas (média/contagem) e os agregados de avaliações de produtos.
- Alterações de estoque não invalidam o catálogo porque os DTOs e os filtros públicos
  atuais não contêm estoque. Se esse contrato mudar, incluir essa invalidação também
  na criação/cancelamento de pedidos.

A invalidação completa por região evita deixar combinações antigas de filtros,
ordenação e totais de paginação. O `TransactionAwareCacheManagerProxy` adia `put`,
`evict` e `clear` até o commit; rollback mantém entradas anteriores e não publica
leituras não confirmadas. Não usar `sync=true` ou `beforeInvocation=true`, pois
essas opções usam operações imediatas e não oferecem a mesma garantia.

Caffeine é **local a cada instância**. A invalidação imediata alcança apenas a JVM
que executou a alteração. Outras réplicas ou alterações diretas no banco são
refletidas pela expiração. Uma leitura já em andamento durante uma alteração também
pode publicar um retrato anterior, limitado pelo TTL a partir dessa publicação.
Não é uma garantia de consistência forte. Para exigir invalidação simultânea entre
réplicas, adicionar eventos distribuídos ou desativar este cache.

Checkout, preço efetivo, disponibilidade/estoque, frete, cupons, pagamentos,
financeiro, painel, pedidos, entregas, GPS, chat, favoritos, funcionários,
autenticação e `/minha-loja` continuam consultando os dados atuais e executando
suas verificações de acesso. A listagem textual de avaliações também permanece
sem cache: contém nomes de usuários editáveis e comentários.

## Configuração

| Variável | Padrão | Efeito |
| --- | --- | --- |
| `NHAC_CACHE_ENABLED` | `true` | `false` substitui o cache por NoOpCacheManager |
| `NHAC_CACHE_CATALOG_TTL` | `30s` | Expiração após escrita para lojas e produtos |
| `NHAC_CACHE_RATINGS_TTL` | `2m` | Expiração dos agregados de notas |
| `NHAC_CACHE_LIST_MAXIMUM_SIZE` | `200` | Entradas por cache de páginas |
| `NHAC_CACHE_DETAIL_MAXIMUM_SIZE` | `2000` | Entradas por cache de detalhes/notas |

TTL e limites precisam ser positivos. As propriedades são lidas no início da
aplicação. `recordStats()` está habilitado nos caches nativos para diagnóstico;
essa configuração não publica um novo endpoint de métricas nem de limpeza.
O limite é de entradas, não de bytes: dimensionar conforme o tamanho dos DTOs.

## Verificação

`CatalogCacheTest` usa os serviços reais através dos proxies Spring, Caffeine real
e transações H2, com repositórios simulados. Verifica reaproveitamento, chaves,
limites de paginação, fechamento de loja, ativação/desativação de produto,
avaliações, autorização e commit/rollback.
`CacheConfigurationTest` verifica expiração, limites, estatísticas, configuração
inválida e desligamento. Os testes não dependem de espera temporal.

Validação local em Java 25: **85 testes passaram**, sem falhas ou erros: 13 novos
testes de cache e 72 testes existentes de lojas, produtos, avaliações e pedidos.
Não foi executada a suíte completa com MariaDB/Testcontainers nesta validação.

Executar com Java 25:

```sh
./mvnw -Dtest=CatalogCacheTest,CacheConfigurationTest,LojaServiceTest,ProdutoServiceTest,AvaliacaoServiceTest test
```

Em ambientes sem suporte a attach dinâmico, fornecer o JAR `mockito-core` como
`-javaagent` ao JVM dos testes, preservando o agente JaCoCo quando habilitado.

## Avaliações do entregador

`entregadorAvaliacoes` armazena somente o resumo imutável AVG/COUNT por entregador, com o TTL de avaliações e tamanho máximo de detalhes. Salvar uma avaliação invalida a chave do entregador usando o CacheManager consciente de transações, após commit. A página de comentários consulta o banco com EntityGraph de usuário. Estado operacional, localização, ofertas e código de conclusão não usam cache.
