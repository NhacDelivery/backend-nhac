# Passo 6 — otimização com medição

## Alterações

- GET `/api/v1/conversas?tipo=LOJA|CLIENTE&page=0&size=20` filtra antes da paginação.
  Sem tipo mantém o contrato anterior. Ordenação estável: ultimaMensagemEm/id desc.
  O filtro mantém restrição de participante e papel; o total é específico da aba.
- Reserva chama `FreteService.calcularTaxa`, o mesmo serviço da estimativa pública.
  Mantém taxa configurada, gratuidade (0) e fallback R$ 5; não muda a regra por distância.
- Nenhum cache, TTL, pool ou índice novo foi adicionado nesta rodada.

Publicar este backend antes do cliente que envia o filtro. Clientes anteriores
continuam usando a listagem mista. A API de chat não fica em cache compartilhado.
Testes HTTP/banco cobrem mistura, segundo participante, privacidade, totais, página
final e desempate de data; frete estimado/reservado cobre 0, 7,35 e taxa ausente.

## Consultas e volume do chat

No Nhac, test/benchmarks/chat_paginacao_benchmark_test.dart compara o algoritmo
original da base 0307b90 com a nova consulta filtrada. A fixture contém 400 conversas
com lojas seguidas de 120 com pessoas; pede página 5, tamanho 20. Valida os mesmos
IDs e conta GETs/bytes JSON: o algoritmo antigo percorre 6 páginas; o novo pede uma.
O resultado é emitido como CHAT_BENCHMARK no job “Passo 6 - paginação e medição”.
Isso mede trabalho e volume do cenário controlado; não é latência de produção.
O ganho de cada usuário depende de quantidade/distribuição de conversas.

## Amostra real do Cloud Run

Arquivo: latency-cloud-run-20261016.json, horário UTC. Foram 18 GETs públicos,
sequenciais, sem autenticação: uma observação inicial e cinco subsequentes para
cada endpoint. Não houve pedido, pagamento, escrita ou execução concorrente.
A revisão implantada não foi verificada; o relatório não a confunde com o SHA do
repositório. O cliente HTTP deste laboratório usou limite de cinco segundos.

| Endpoint | p50 das respostas posteriores bem-sucedidas | p95 | Falhas / total |
|---|---:|---:|---:|
| Health | 2197,12 ms | 2277,04 ms | 3 / 6 |
| Catálogo de 50 produtos | 2081,64 ms | 3172,39 ms | 3 / 6 |
| Categorias | 2528,54 ms | 2659,71 ms | 3 / 6 |

As nove falhas tiveram status 0 (transporte/timeout, aproximadamente cinco segundos),
não um código HTTP do backend. Os percentis excluem essas falhas e têm apenas três
respostas bem-sucedidas por endpoint; não descrevem a cauda completa nem um SLA.
A primeira observação não prova cold start e respostas posteriores não provam hit
de cache. Os bytes são do JSON sem compressão. Essa amostra inclui rede/TLS e o
caminho deste ambiente até a hospedagem; sem logs/traces não permite atribuir a
causa ao Cloud Run, banco, aplicação ou rede do laboratório.

O health apresentou o mesmo padrão de falhas do catálogo. Antes de ampliar cache,
correlacionar horário com logs do Cloud Run (request latency, status, cold starts),
latência/espera de conexão do banco e métrica http.server.requests. Comparar no
mesmo ponto de medição, revisão e tamanho de página. Preservar a amostra original,
inclusive falhas, em vez de repetir até conseguir apenas números bons.

## Reproduzir

Teste do medidor (não faz rede externa):

    python3 -m unittest discover -s tools -p 'test_measure_latency.py' -v

Amostra local de leitura:

    python3 tools/measure_latency.py --base http://127.0.0.1:8080 --output /tmp/latency-local.json

Amostra explícita de baixa frequência no ambiente público já autorizado:

    python3 tools/measure_latency.py --base https://backend-nhac.cloud.run --allow-remote-reads --samples 5 --output /tmp/latency-cloud-run.json

O medidor aceita no máximo dez observações posteriores por endpoint, uma por vez,
com pausa mínima de 200ms; não segue redirects, não recebe tokens e só executa os
três GETs fixos. Retorna código 1 se houver falha, preservando o relatório. O
benchmark de carga comparativo antigo continua em tools/benchmark_http.py e exige
laboratório localhost isolado. Não executar carga de concorrência na produção.
