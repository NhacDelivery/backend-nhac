# Desempenho: implementação e medição
## Configuração atual
| Recurso | Padrão |
| --- | --- |
| Hikari máximo / mínimo | 8 / 1 conexões por instância |
| Espera para obter conexão | 30 segundos |
| HTTP de gateways | Conexão 4 s; leitura 10 s |
| Cache público | Caffeine local, TTL de catálogo 30 s |
| Páginas via Pageable | Máximo 100 itens |
| Scheduler | Pool de 3 threads |

A configuração executável está em application.properties, não neste resumo. Ajuste pelo limite real do banco, quantidade de réplicas e métricas de espera.

## Redução de trabalho
- Financeiro agrega status, horários e categorias no banco. Não carrega todas as entidades de pedidos e itens; o ranking tem limite 10 na consulta.
- Comentários consultam quantidade de curtidas, estado do usuário e nomes das respostas em lote. A listagem não carrega todos os IDs de quem curtiu.
- Reserva de checkout e finalização de cancelamento têm transações curtas. Consultas e cancelamentos nos gateways ficam fora dos locks.
- Criação de cliente Asaas usa claim persistido por chave única. Não há locks globais por faixa de hash. Um resultado incerto é recuperado por externalReference; ausência continua exigindo conciliação, sem repetir POST cegamente.
- Fingerprint novo usa representação estruturada e inclui troco/CPF normalizados. Pedidos antigos preservam o algoritmo versão 1 para replay; novos usam versão 2.

Não foi atribuído percentual de ganho a essas mudanças. Consultas menores e locks mais curtos precisam ser avaliados com a mesma base, revisão, hardware e ponto de medição.

## Medir
Primeiro execute os testes do medidor, sem rede:
```bash
python3 -m unittest discover -s tools -p 'test_measure_latency.py' -v
```
Para amostra sequencial local:
```bash
python3 tools/measure_latency.py --base http://127.0.0.1:8080 --output /tmp/nhac-latencia.json
```
Observe p50/p95, falhas, SQL por resposta, heap e tempo de espera do Hikari. Não tire a falha do relatório para melhorar artificialmente o percentil.

Benchmarks comparativos de carga e criação de base exigem laboratório isolado em localhost. O perfil e2e restaura fixtures; nunca use dados reais nele. [As medições antigas](historico/README.md) mantêm seus resultados e ressalvas.

## Escala
Caffeine e o broker STOMP são locais. Outra réplica não recebe automaticamente invalidação ou eventos da primeira. CPU sempre disponível/execução externa é necessária para jobs no Cloud Run. Alterar cache ou quantidade de conexões não resolve ausência de coordenação entre instâncias.
