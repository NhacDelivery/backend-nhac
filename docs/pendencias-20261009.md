# Integração e correções de 09/10/2026

A branch `feature/chat-clientes-20261008` integra os contratos de `fix/motoboy-contratos-20261005` e mantém os recursos de feed, adicionais e chat de clientes. Inclui recuperação/correção de coordenadas, histórico com endereço completo, expiração de disponibilidade, avisos persistentes, preferências de push, repasses, suporte, validação de bicicleta/Pix/documentos e confirmação de troca de telefone. A integração preserva o envio de mensagens de clientes após commit e acrescenta o aviso de mensagens para motoboys.

Novas correções: preferências de comida por conta com diferença entre nunca escolhidas e escolha vazia; edição do feed idempotente por usuário/publicação; destinatário no payload push; AASA para Universal Links; retirada/transferência efetiva de corrida com bloqueios transacionais e preservação do estado terminal do suporte. O agente Mockito é carregado explicitamente no Java 25.

## Atualização do banco

As migrations V1015–V1018 do PR #138 são preservadas integralmente, com as mesmas versões e checksums da main. V1020 adiciona preferências e V1021 dados da ação de suporte. As cópias antigas e renumeradas de telefone, suporte, avisos e repasses foram removidas; não existem versões repetidas na branch integrada.

Antes do deploy, verificar o histórico Flyway e duplicatas de telefone após normalização. A criação do índice único exige resolver números que pertençam a mais de uma conta; a migration não escolhe silenciosamente uma conta. Se um banco já aplicou as versões da branch isolada de contratos do motoboy, não aplicar a branch integrada automaticamente: fazer backup e alinhar o histórico com os scripts realmente aplicados antes de migrar. Não executar repair indiscriminadamente.

## Atendimento de corrida

Somente ADMIN ativo e sem vínculo com loja pode usar `PUT /api/v1/suporte/entregas/{protocolo}/acao`.

- Retirada antes da coleta: `{"acao":"RETIRAR","entregaFisicaConfirmada":false}`. Libera o entregador, invalida ofertas antigas e reabre o despacho; a resposta confirma que a loja acompanha a busca de outro responsável.
- Transferência: `{"acao":"TRANSFERIR","novoUsuarioId":"ID_DO_ENTREGADOR","entregaFisicaConfirmada":true}`. O novo entregador precisa estar ativo, online e sem corrida. Após coleta, a confirmação da entrega física é obrigatória. O protocolo registra o novo responsável.
- Repetir a mesma ação concluída retorna a confirmação existente; ação incompatível é rejeitada. Uma resposta textual não apaga a conclusão da retirada/transferência.

## Configuração externa

Configurar `NHAC_ANDROID_SHA256_CERTIFICATES` e `NHAC_IOS_TEAM_ID` para links HTTPS. Push exige `FIREBASE_PROJECT_ID` e `FIREBASE_PUSH_CREDENTIALS_BASE64`, da conta de serviço correta, além das configurações dos aplicativos. Rota de bicicleta exige `OSRM_CYCLING_URL` com serviço que suporte o perfil correto; mock não valida rota real. O perfil E2E exige `E2E_PASSWORD` temporária de pelo menos 12 caracteres e não usa mais senha fixa no loader.

## Testes

A integração preserva as correções e os testes do PR #138, incluindo credenciais temporárias do E2E, autorização administrativa, DTOs, credenciais de push reutilizadas e atomicidade da alteração de telefone. A matriz de autorização cobre também a nova ação de suporte. Os checks e resultados da validação desta integração estão registrados no PR #145.
