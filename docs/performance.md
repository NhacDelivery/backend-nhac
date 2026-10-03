# Desempenho e latência — 03/10/2026

Base: main `76096cd9549641525cbda719cf6ae1bf9204cd45`. Implementação em `perf/latencia-tcc`. O Nhac correspondente parte de `fix/ux-estados-e-erros`.

## Mudanças

| Área | Implementação | Efeito esperado |
|---|---|---|
| Catálogo | GET /produtos/cards e /produtos/cards/promocoes, sem percorrer adicionais | Menos SQL e JSON; endpoints antigos continuam disponíveis |
| Consultas | Batch fetch 50 e EntityGraph de loja/entregador nos pedidos | Reduz consultas adicionais sem paginar sobre JOIN FETCH de coleção |
| Banco | V1008: índices compostos para produtos e pedidos, cliente de pagamento persistido e flag de criação inconclusiva | Apoia filtros frequentes e recuperação do pagamento |
| Checkout | Reserva transacional separada do gateway; dados enviados como snapshot | Banco e trava do usuário não ficam presos à chamada externa; OSIV não faz flush de setters do gateway |
| PIX | Reutiliza cliente Asaas por ambiente, usuário e CPF normalizado | Evita criar o mesmo cliente a cada pedido |
| Cartão | Chave Stripe estável por pedido; nenhuma repetição automática de POST pelo SDK | Recuperação usa a mesma chave, sem gerar outro PaymentIntent |
| Integrações | Connect 4 s, read 10 s em RestTemplate e Stripe | Limita chamadas que ficavam sem limite explícito |
| Conciliação | Pausa de 60 s após falha por pedido, cache limitado a 500 entradas | Evita consultar o mesmo pagamento inconclusivo a cada 5 s |
| Recursos | Hikari padrão máximo 8 / mínimo 1, espera 5 s; scheduler com 3 threads | Menos conexões ociosas e menor interferência entre tarefas |
| HTTP | Compressão para JSON acima de 2 KB, página máxima 100, histogramas e faixas de latência | Menos bytes na rede e base para observar p95 e saturação |
| Rota | Cache limitado a 256 trajetos por 15 minutos, incluindo coordenadas na chave | Evita repetir OSRM para o mesmo trajeto; erros não entram no cache |
| Web | CORS permite Idempotency-Key somente nas origens já autorizadas | Checkout web consegue usar a proteção existente |

## Checkout: comportamento a revisar

A reserva é persistida antes do gateway. Se a chamada externa falhar, a resposta inclui `details.pedidoId` com `PAGAMENTO_INDISPONIVEL`. O Nhac abre a recuperação desse pedido. Repetir a mesma Idempotency-Key retorna o pedido existente e não reserva estoque ou cupom novamente.

Uma cobrança inconclusiva conserva o estoque e o cupom reservados. Cancelamento e expiração só liberam recursos depois da conciliação. PIX sem identificador é procurado por externalReference; resultado ausente ou múltiplo não autoriza outra cobrança. Cartão sem identificador usa a chave original durante até 23 horas; depois exige conciliação manual para evitar recriar um pagamento fora da janela de retenção da chave.

Erros definitivos de criação, anteriores à cobrança ou rejeições conhecidas, permitem cancelar a reserva com segurança. Vínculos de pagamento atualizam campos específicos e incrementam version: uma cópia anterior do pedido não pode apagar o identificador recebido. Falha no vínculo não entrega QR ou client secret como se a gravação tivesse funcionado.

O tratamento de pagamentos inconclusivos é conservador e exige acompanhamento operacional. Não apagar a flag ou devolver estoque só porque ocorreu timeout; conferir a referência no provedor antes de resolver manualmente. Esta rodada não implementa uma fila persistente/outbox nem painel administrativo de conciliação.

## Evidência local

Java 25, MariaDB 11.8.9 em Docker, dois bancos isolados, JVMs com Xms64m/Xmx256m. Catálogo: 300 produtos sintéticos mais a fixture original; cada produto sintético tem dois grupos com três adicionais. Ambas as APIs retornam 50 produtos. Nenhum teste de carga foi executado na Render.

| Medida | Base | Otimizado |
|---|---:|---:|
| SELECTs de catálogo por resposta sem cache, contados no general_log | 104 | 2 |
| JSON sem compressão, listagem aquecida | 33.486 bytes | 15.307 bytes |
| p95 de 30 chaves distintas de cache | 217,20 ms | 61,34 ms |
| p95 de 100 leituras sequenciais aquecidas | 23,08 ms | 13,06 ms |
| p95 de 200 leituras aquecidas, concorrência 8 | 25,78 ms | 38,13 ms |
| Erros nas 330 leituras por versão | 0 | 0 |

A redução de bytes é aproximadamente 54%. O resultado concorrente não melhorou nessa execução. Havia processos de desenvolvimento/teste no mesmo Codespaces; estes números são uma observação local, não um SLA, nem prova de ganho uniforme em produção. A contagem de SQL e os bytes mostram a redução de trabalho de forma mais direta.

Artefatos: [HTTP](benchmark-local.json), [SQL](benchmark-sql.json). O contador SQL usa Query/Execute e exclui a conexão que lê o log; Prepare não é contado como execução.

## Reproduzir

1. Criar um worktree da base e gerar seu jar. Gerar o jar otimizado separadamente.
2. Rodar MariaDB isolado chamado `nhac-perf-isolado`, com bancos `nhac_perf` e `nhac_perf_baseline`; usuário local `nhac_perf`, senha local `nhac_perf_local`, root `nhac_perf_root_local`. Publicar apenas 127.0.0.1:3309.
3. Iniciar ambos com perfil e2e, portas 8088 (base) e 8089 (otimizado), o mesmo limite de memória e seus respectivos bancos. O E2EDataLoader apaga dados do banco ao iniciar: esses bancos devem ser descartáveis.
4. Executar `python3 tools/seed_perf.py` e depois `python3 tools/benchmark_http.py --output docs/benchmark-local.json`.
5. O benchmark aceita apenas localhost e realiza GETs. Esperar a expiração de cache ou reiniciar os servidores antes de repetir a fase de chaves distintas. Não reconstruir um jar enquanto o servidor o está usando; copiar o jar para um caminho estável antes de iniciar.

As credenciais desses scripts são exclusivas do laboratório local, não credenciais de produção.

## Publicação e operação

Publicar primeiro o backend com V1008 e os novos endpoints, depois o Nhac. Os índices adicionados consomem espaço e podem exigir uma janela de migração conforme o tamanho real das tabelas; Flyway foi validado em MariaDB e sua segunda execução não reaplica migrations.

Os valores DB_POOL_MAX e DB_POOL_MIN continuam configuráveis. Observar espera de conexão, heap, CPU, p95, erros de gateway e reservas inconclusivas antes de aumentar o pool ou a concorrência. Histogramas não significam endpoint público de métricas: preservar acesso restrito ao expor observabilidade.

Redis, busca full-text, cache de permissões, WebSocket unificado e migração de hospedagem não foram introduzidos. Busca full-text muda a semântica atual de substring; cache de permissões requer política de revogação; mudanças de socket precisam de testes de sessão, reconexão e múltiplos pedidos. Os mecanismos atuais continuam operando.

## Hospedagem para o TCC

Fontes consultadas em 03/10/2026:

| Opção | Custo/benefício informado pelo provedor | Avaliação |
|---|---|---|
| Render Free | Suspende após 15 min sem tráfego; despertar leva cerca de 1 min | O código não elimina esse tempo de despertar |
| Render pago | Starter anunciado a US$ 7/mês, 512 MB; confirmar preço vigente | Caminho de menor migração, mas medir memória do Java e custo do banco |
| Railway Hobby | Mínimo de US$ 5/mês, abatido do uso; consumo maior aumenta a conta | Conveniente para Docker; backend e banco entram no orçamento |
| Azure Student Pack | US$ 100 em créditos e serviços, oferta para estudantes de 18+ | Primeira opção a verificar para economizar no TCC; benefício depende de aprovação e limites |

Usar backend e banco na mesma região sempre que possível. Uma VM com containers mantém MariaDB e Java sem trocar a tecnologia do banco, mas exige TLS, persistência, backup e administração. Não é necessário introduzir Kubernetes ou microserviços para esta escala.

Fontes: [Render Free](https://render.com/docs/free), [Render Pricing](https://render.com/pricing), [Railway](https://docs.railway.com/pricing), [GitHub Pack](https://education.github.com/pack). Nenhum recurso pago foi provisionado e a hospedagem existente não foi alterada.
