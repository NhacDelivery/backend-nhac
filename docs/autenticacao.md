# Autenticação e cadastro
## Cadastro por e-mail
1. POST `/api/v1/auth/enviar-codigo-cadastro` com `{"email":"pessoa@exemplo.com"}`.
2. POST `/api/v1/auth/confirmar-email-cadastro` com e-mail e código.
3. Guarde o `tokenCadastro` retornado na resposta 200.
4. POST `/api/v1/auth/registrar` com nome, e-mail, telefone, senha e esse token.
5. Use `usuarioId` e `token` retornados pelo servidor para a sessão.

Exemplo de conclusão:
```json
{
  "nome": "Pessoa",
  "email": "pessoa@exemplo.com",
  "telefone": "11912345678",
  "senha": "SenhaExemplo123",
  "tokenCadastro": "valor-retornado-na-confirmacao"
}
```
O campo legado `id` é opcional e ignorado. Atualizar dados de uma conta existente usa seu endpoint autenticado; cadastro só insere nova identidade.

A prova tem 256 bits aleatórios, fica armazenada como SHA-256, pertence ao e-mail confirmado, vale por 30 minutos após validação e é consumida na transação do cadastro. Reenviar código invalida a prova anterior. Invalidar um OTP não valida o e-mail. Após três erros, um OTP não pode mais ser confirmado.

**Integração necessária nos apps:** versões que apenas verificavam o HTTP 200 e enviavam o ID local precisam capturar e enviar tokenCadastro, além de adotar usuarioId da resposta. Coordenar essa atualização antes de integrar/publicar o backend. Provas antigas não são convertidas automaticamente: o usuário precisa confirmar um novo OTP. Não registrar o comprovante em logs.

## Demais logins
- Senha: POST `/api/v1/auth/login`.
- Google: POST `/api/v1/auth/social`, com ID token validado pelo Google.
- SMS: envio/validação em `/api/v1/verificacao-telefone` e login em `/api/v1/auth/login-sms`.
- JWT: validade de 30 dias contados como duração UTC, sem refresh token. HTTP 401 exige nova autenticação.

## Limite de entrada
Os POSTs de autenticação e verificação de telefone passam por interceptor MVC, antes do controller. São até 10 requisições por IP e rota em janela de 15 minutos, incluindo tentativas inválidas; excesso retorna HTTP 429 no formato padrão.

O armazenamento Caffeine tem expiração e limite de 10.000 chaves; atualização da contagem é atômica por chave. O limite é **por instância** e reinicia com o processo; não substitui proteção distribuída no gateway ao escalar réplicas. A expulsão por capacidade também limita a garantia sob grande volume de chaves distintas.

O código usa remoteAddr e não confia diretamente em X-Forwarded-For. Atrás de proxy, configure o encaminhamento com uma lista de proxies confiáveis e sanitização no ingresso. Sem essa configuração, clientes podem compartilhar o IP do proxy e o mesmo limite.

## WebSocket
CONNECT autentica o JWT. SUBSCRIBE exige usuário e participação nos canais reconhecidos. Curingas e canais desconhecidos são negados. SEND aceita somente `/app/conversas/{id}/enviar`; a autorização de participação continua no ChatService. Clientes não podem publicar diretamente em `/topic` ou `/queue`.
