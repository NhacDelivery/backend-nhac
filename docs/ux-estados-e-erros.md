# Contratos de UX e pedidos simultâneos

Branch: `fix/ux-estados-e-erros`, base main `22b7796`. Implantar antes do Flutter da branch homônima. Nenhuma migração destrutiva ou publicação direta na main.

- `GET /pedidos/ativos`: lista completa do usuário autenticado, ordenada por criação decrescente. O singular permanece por compatibilidade.
- `POST /pedidos`: endereço exige latitude e longitude finitas, dentro dos limites e diferentes do par `0,0`. O DTO persiste ambas no pedido. Compras distintas podem coexistir; idempotência por chave/fingerprint, lock de usuário, estoque e pagamento permanecem.
- `GET /produtos/promocoes`: Page com paginação/sort, loja aberta, produto ativo, percentual de desconto positivo e `preco < 20`. `preco` é o valor efetivamente cobrado, não reaplicar desconto.
- `GET /entregas/{id}/rota`: ausência/invalidade de coordenadas e resposta inválida do OSRM retornam a causa de negócio. Fora do modo mock explícito, não gerar reta/distância/tempo substitutos. Lojas novas sem posição confirmada precisam atualizar o cadastro de localização; não inferir posição do texto. Pedidos antigos não possuem referência confiável para backfill geográfico automático.
- Localização 204 continua sendo ausência de posição recente/disponível. Clientes devem respeitar intervalo de atualização, sem retry imediato.
- Chat mantém o `conteudo` textual legado (`Produto`, `ID`, `Preço`, `Imagem` opcional, linha vazia, mensagem). Flutter e painel interpretam o mesmo cartão. Preservar `clientMessageId` ao reenviar; a idempotência existente evita duplicação.

Validação local: testes comuns e PedidoFlowIT em H2; RotaServiceTest com OSRM simulado. `mvn verify` também requer Docker para MariaDB/concorrência; acompanhar CI. Nenhuma entrega real ou rota de produção foi validada nesta alteração.
