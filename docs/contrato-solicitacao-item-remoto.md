# Solicitação de item por participante remoto

## Rotas

Base: `/api/compras/{compraId}/solicitacoes-itens`, autenticada via JWT.
A rota já publicada foi preservada. O contexto familiar é obtido da Compra;
o cliente não informa usuário ou solicitante.

- `POST` na base: cria solicitação e retorna `201`, representação e `Location`.
- `GET` na base: retorna array ordenado por `criadoEm` e ID.
  Filtro opcional `?status=PENDENTE|APROVADA|RECUSADA`.
- `POST /{solicitacaoId}/aprovar`: retorna `200` com solicitação atualizada.
- `POST /{solicitacaoId}/recusar`: retorna `200` com solicitação atualizada.

Payload da criação:

```json
{"descricao":"Leite","quantidade":1.500,"unidadeMedida":"LITRO"}
```

Descrição obrigatória, não branca, até 200 caracteres; preservada como nos
itens existentes, sem normalização adicional. Quantidade e unidade são
opcionais. Quantidade decimal positiva representável em `numeric(12,3)`
(mínimo 0.001, até nove dígitos inteiros e três decimais).
Unidade reutiliza `UnidadeMedida`. Categoria pertence à Compra/lista, não a um
novo produto canônico. Solicitações repetidas não são mescladas.

## Autorizações e decisão

Criação: vínculo familiar ativo, família ativa, participação na Compra,
Compra `EM_ANDAMENTO` e presença exatamente `NAO_PRESENTE`. `PRESENTE` e
`NAO_INFORMADA` recebem `409`. A regra presencial de colocar no carrinho
continua exigindo presença física. O fluxo preexistente de adição de item
pendente não foi alterado.

Listagem: participante com vínculo ativo em família ativa. Permite consultar
também decisões históricas, mas não concede acesso a observadores ou externos.

Decisão: somente o responsável operacional **atual**, presente e com vínculo
ativo. Papel `ADMINISTRADOR` não concede essa autoridade. A solicitação deve
pertencer à Compra, estar `PENDENTE`, e a Compra continuar `EM_ANDAMENTO`.

Aprovação cria exatamente um `ItemCompra PENDENTE` por
`ItemCompra.criarDuranteCompra`, copiando descrição, quantidade e unidade,
sem inserir ItemLista e sem colocar automaticamente no carrinho. A autoria
da inclusão efetiva é do responsável que aprovou; a autoria da solicitação
permanece no pedido. Recusa não cria item. Ambas registram decisor e instante.

Erros: payload inválido `400`, não autenticado `401`, acesso/autoridade negados
`403`, Compra/solicitação inexistente ou solicitação de outra Compra `404`,
Compra encerrada ou segunda decisão (inclusive repetição da mesma) `409`.

## Representação e polling

Cada solicitação retorna `id`, `descricao`, `quantidade`, `unidadeMedida`,
`status`, `criadoEm`, `solicitanteParticipanteCompraId` (campo preservado),
`solicitante: {id,nome}`, `decididoEm` e `decisor: {id,nome}`.
IDs de solicitante/decisor são de `ParticipanteCompra`; nomes são snapshots
históricos da participação. Os campos de decisão são nulos em pendentes.

O GET existente `/api/familias/{familiaId}/listas/{listaId}/compra` recebe
`solicitacoesItensPendentes`, sem novo polling. Durante andamento, participantes
autorizados recebem todas as pendentes; observadores e Compras encerradas
recebem array vazio. Aprovadas/recusadas não entram nessa coleção.
`contextoUsuario.podeSolicitarItemRemoto` e
`contextoUsuario.podeDecidirSolicitacoesItens` expressam as permissões atuais;
os comandos sempre revalidam a autoridade, independentemente do snapshot.
Pendentes em Compras encerradas permanecem históricas, sem novo status nem
decisão automática.

## Persistência, concorrência e consultas

V24 permanece inalterada; nenhuma nova migration é necessária. Mantidos os
IDs escalares de Compra e ParticipanteCompra com FKs e índice `(compra_id,status)`.
O domínio valida pertencimento do solicitante e do decisor à mesma Compra.

Criação/decisão transacionais usam a ordem global de locks
`ListaCompra -> Compra -> SolicitacaoItemCompra` (último somente na decisão).
`PESSIMISTIC_WRITE` protege a solicitação; o lock da Compra serializa também
finalização, presença, transferência operacional e ordenação de novos itens.
Uma segunda decisão recebe conflito após a primeira transação terminar;
solicitação e ItemCompra são gravados atomicamente.

Solicitantes/decisores são resolvidos por uma consulta em lote dos participantes
com o EntityGraph existente (membro/usuário). Mapeamento por ID em memória;
nenhuma consulta por solicitação. No polling, os participantes já carregados
são reutilizados e há apenas uma consulta adicional para as pendentes.
Teste de integração compara contagem de statements com um e 21 pedidos,
de participantes diferentes, incluindo decisões, e também no polling.

## Validação

`SolicitacaoItemCompraTests`: estado inicial, dados, decisões únicas,
pertencimento, instante e validações do modelo.
`SolicitacaoItemCompraIntegrationTests`: PostgreSQL real com Flyway/Hibernate,
serviços, endpoints com JWT, autorização, histórico, polling, ausência de N+1
com estatísticas Hibernate e duas transações concorrentes (aprovar/aprovar e
aprovar/recusar). Sem Open Session in View.

Nesta entrega, os 25 testes focados passaram (5 unitários e 20 de integração,
incluindo concorrência real, rollback e isolamento entre famílias). Os testes
relacionados da API, consulta da Compra e adição concorrente também passaram.

`mvn test`: 487 testes executados, 484 passaram, nenhuma omissão; somente as
três falhas históricas autorizadas (2 falhas de asserção e 1 erro):

- `PresencaOperacionalIntegrationTests.migraV9SemInventarPresencaEValidaConstraints`:
  copia `select *` de `membro_familia` atual para schema V9 com menos colunas.
- `CompraV8IntegrationTests.flywayAplicaV1AteV9EHibernateValidaSchema`:
  espera exatamente V1–V14, mas o projeto já possui V1–V24.
- `ItemCompraV9IntegrationTests.flywayAplicaV9EHibernateValidaSchema`:
  mesma expectativa desatualizada de V1–V14.

Esses testes/migrations históricos não foram alterados nem excluídos.

`mvn verify` real, sem `skipTests`: repetiu os 487 testes, com os mesmos
484 aprovados e somente as três falhas acima. Ambos os comandos encerraram
com código 1 exclusivamente por essas falhas históricas; não houve falha nova.
`git diff --check` e a revisão final do diff foram aprovados.
