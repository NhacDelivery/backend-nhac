# Operação de pagamentos — passo 5

## Cancelamento e devolução

POST `/api/v1/pedidos/{id}/estorno` aceita somente o cliente dono e pedido PAGO,
antes de PREPARANDO, com cobrança eletrônica conhecida. Retorna 202. Cancela o
pedido, devolve estoque/cupom e grava SOLICITADO na mesma transação sob lock.
Replay retorna 202 sem nova devolução. O cancelamento pendente anterior continua
em PATCH `/pedidos/{id}/cancelar`. Dinheiro não tem devolução eletrônica.

O worker consulta no máximo 50 registros por execução, com intervalo de 60s por
registro. SOLICITADO -> ENVIANDO é um update condicional commitado antes do POST
externo. Duas instâncias não criam o mesmo estorno. Nenhum HTTP externo mantém o
lock/transação do cancelamento. Somente confirmação integral vira CONCLUIDO.
Timeout, crash após claim, rejeição ou parcial passam a consulta/revisão; nunca
repetir o POST automaticamente. Stripe usa também chave nhac-estorno-{pedidoId}.
Resultados de consulta usam CAS de estado para não rebaixar CONCLUIDO.

ENVIANDO sem POST (crash entre claim e envio) exige análise; esse compromisso evita
débito duplicado quando não se pode distinguir crash antes/depois do provedor.
Não há botão que apenas marque o estorno como concluído sem confirmação externa.

## Acompanhamento

GET `/api/v1/pedidos/{id}/operacao-pagamento` retorna estado, atualizadoEm e
podeSolicitarEstorno somente ao dono; não retorna identificadores externos.
GET `/api/v1/operacao/pagamentos?page=0` lista pendências, 20 por página, ordenadas
por criadoEm/id. Fila, métricas e disparo manual exigem ADMIN ativo, sem vínculo
com loja (mesma política de suporte de entregas).

Na fila REVISAO: conferir o pedido no painel do gateway, valor integral, saldo,
falha e identificador da cobrança. Se existir devolução, aguardar a consulta.
Se o provedor rejeitou definitivamente e não existe estorno, realizar a devolução
integral no painel autorizado do gateway; o worker detecta a confirmação depois.
Não alterar o banco para SOLICITADO, não criar um novo pedido para compensar e não
reenviar uma cobrança/estorno de resultado incerto. Casos parciais/múltiplos Stripe
exigem análise manual e não são automaticamente declarados concluídos.

## Métricas

`nhac.pagamentos.operacoes` no MeterRegistry usa somente etiquetas fixas etapa e
resultado. Etapas: criacao, consulta_pix, conciliacao, estorno_enviar,
estorno_consultar. Resultados: falha, consultado, pendente, concluido.
GET `/api/v1/operacao/pagamentos/metricas` permite leitura protegida desses contadores.
São contadores por instância e reiniciam junto dela; coletar no monitoramento para
histórico/agregação. A fila no banco é a fonte durável. Não usar IDs, CPF ou tokens
como etiquetas. Actuator continua sem exposição pública de métricas.

## Publicação no Cloud Run

Aplicar V1023 e publicar backend antes do app. O job @Scheduled exige CPU disponível
fora de requisições e instância ativa. No Cloud Run, verificar faturamento por
instância/CPU sempre alocada e mínimo de instâncias, ou usar execução externa
protegida do processamento. Não presumir que @Scheduled funciona após scale-to-zero.
POST `/api/v1/operacao/pagamentos/processar` permite disparo síncrono por ADMIN;
usa a mesma fila/CAS, sem burlar o período mínimo. Não embutir token de usuário em
app ou URL de agendamento. Esta mudança não altera faturamento/escala da produção.

Verificar no sandbox com saldo antes de habilitar produção. Asaas pode rejeitar
estorno integral por saldo insuficiente após taxas. Status do pedido cancelado é
distinto de estorno concluído; a interface não promete prazo fixo de devolução.

Referências oficiais consultadas em 15/10/2026:
- https://docs.asaas.com/reference/estornar-cobranca
- https://docs.stripe.com/api/refunds/create?lang=java
- https://docs.stripe.com/api/refunds/list?lang=java
- https://docs.cloud.google.com/run/docs/configuring/billing-settings
