# Como mudar e testar
1. Abra uma branch a partir de main e descreva qual comportamento está errado.
2. Escreva teste de regressão com uma entrada que falha antes da correção.
3. Faça a menor alteração que preserve os contratos não afetados.
4. Execute os testes relevantes e depois a suíte exigida pelo CI.
5. Atualize DTO/Swagger e documentação se o contrato mudou.
6. Abra PR com problema, comportamento final, testes e limitações.

## Comandos
```bash
./mvnw test
./mvnw verify
```
Para investigar apenas uma classe:
```bash
./mvnw -Dtest=CadastroSegurancaIT test
./mvnw -Dtest=PedidoServiceTest,FingerprintPedidoTest test
```
O primeiro comando direcionado executa um IT H2 pelo Surefire. A suíte verify normal executa os ITs pelo Failsafe e precisa de Docker para MariaDB/Testcontainers.

## O que verificar
- Cadastro: ID recebido não altera outra conta; prova inválida/reutilizada é recusada.
- OTP: reenvio não comprova acesso; tentativas incorretas ficam persistidas.
- Autorização: teste dono, outra conta, funcionário sem cargo e conta desativada.
- Estoque: teste sucesso, insuficiência e concorrência entre pedidos distintos.
- Pagamento: timeout não vira sucesso nem libera reserva sem conciliação.
- WebSocket: cliente não publica como servidor, nem assina canal alheio ou curinga.
- Financeiro: valores, intervalo de datas, horário e cancelamentos preservam o contrato.

Use mocks para isolar o gateway, não para substituir a regra que está sendo testada. H2 não substitui validação de migrations e concorrência no MariaDB.

## Arquivos e organização
Crie documentos atuais em docs e mantenha o índice. Relatórios datados pertencem ao histórico, com SHA/base e procedimento. Não edite migrations aplicadas nem apague scripts apenas por terem nomes antigos. Não inclua credenciais, ZIPs, target ou logs de execução no commit.
