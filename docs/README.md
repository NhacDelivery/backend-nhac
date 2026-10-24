# Documentação do backend
## Guias atuais
- [Arquitetura](arquitetura.md): divisão do projeto e fluxo completo.
- [Autenticação e cadastro](autenticacao.md): contrato da prova de e-mail, JWT e limites.
- [Contribuir e testar](contribuir.md): comandos e rotina de mudança.
- [Permissões do lojista](PERMISSOES-LOJISTA.md).
- [Pagamentos e estornos](operacao-pagamentos.md).
- [Desempenho](performance.md) e [cache Caffeine](cache-caffeine.md).
- [Feed](feed-api.md) e [links de publicações](compartilhamento-publicacoes.md).
- [Chat entre clientes](chat-clientes.md).
- [Telefone único](telefone-unico.md).
- [Contratos de UX e pedidos simultâneos](ux-estados-e-erros.md).
- [E2E do cliente](e2e-ci.md).
- [Gerador de catálogo social de desenvolvimento](catalogo-social-dev.md).

## Evidências históricas
[histórico](historico/README.md) preserva integrações e benchmarks com suas bases e limitações. Para entender o comportamento atual, comece pelos guias acima e confirme controllers/DTOs/testes.

Ao mudar um contrato, atualize o guia correspondente no mesmo PR. Não registre “testes passaram” sem indicar a revisão e a execução que produziu o resultado.
