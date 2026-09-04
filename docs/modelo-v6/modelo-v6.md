# Modelo de Dominio v5

Estado completo apos V1, V2, V3, V4 e V6.

## Decisoes de persistencia

- Todas as chaves usam UUID e os instantes usam `TIMESTAMPTZ`.
- Enums sao armazenados como `VARCHAR` com `CHECK CONSTRAINT`.
- Flyway e o unico responsavel pelo schema; migrations anteriores sao imutaveis.
- `usuario.senha_hash` e obrigatorio e nunca armazena senha em texto puro.

## Entidades

### Usuario

- `id`, `nome`, `email`, `senha_hash`, `criado_em`, `atualizado_em`.
- `email` e unico.

### Familia

- `id`, `nome`, `codigo_ingresso`, `status`, `criada_por_usuario_id`, `criada_em`, `atualizada_em`.
- `codigo_ingresso` e unico; `status`: `ATIVA`, `INATIVA`.

### MembroFamilia

- `id`, `familia_id`, `usuario_id`, `papel`, `status`, `apelido`, `criado_em`, `atualizado_em`.
- `UNIQUE (familia_id, usuario_id)` impede duplicidade na mesma familia.
- Um `Usuario` possui de zero a N vinculos `ATIVO` em familias diferentes. O indice parcial nao unico em `usuario_id` para `status = 'ATIVO'` acelera essa consulta.
- `papel`: `ADMINISTRADOR`, `MEMBRO`; `status`: `ATIVO`, `INATIVO`.

### SolicitacaoEntradaFamilia

- `id`, `familia_id`, `solicitante_usuario_id`, `status`, `solicitada_em`, `resolvida_por_membro_familia_id`, `resolvida_em`.
- Ha no maximo uma solicitacao `PENDENTE` por par familia e solicitante.
- `PENDENTE` e `CANCELADA` nao possuem resolucao; `APROVADA` e `REJEITADA` possuem responsavel e instante.
- `status`: `PENDENTE`, `APROVADA`, `REJEITADA`, `CANCELADA`.

### ListaCompra e ParticipanteLista

- Lista: `id`, `familia_id`, `nome`, `categoria`, `estabelecimento`, `status`, `criada_por_membro_familia_id`, auditoria.
- Participante: `id`, `lista_compra_id`, `membro_familia_id`, `entrou_em`, `saiu_em`; unico por lista e membro.
- Lista: `EM_PREPARACAO`, `EM_COMPRA`, `FINALIZADA`, `CANCELADA`.

### ItemLista

- `id`, `lista_compra_id`, descricao, quantidade, unidade, marca, observacoes, `ordem_exibicao`, `removido_em`, responsavel e auditoria.
- Itens ativos usam `removido_em IS NULL` e sao indexados por lista e ordem.

### Compra, ParticipanteCompra e ItemCompra

- Compra preserva snapshots de lista e possui uma unica origem por lista, status `EM_ANDAMENTO`, `FINALIZADA` ou `CANCELADA`.
- ParticipanteCompra preserva membro e papel snapshot; ItemCompra preserva item snapshot, ordem e estados de compra/remocao.
- Todos os FKs, checks de snapshot, remocao e reabertura das V1-V4 permanecem inalterados.

## Regras estruturais

- Uma familia pode possuir diversos membros ativos, inclusive usuarios que participam de outras familias.
- `CANCELADA` permanece para cancelamento explicito futuro e nao e aplicada automaticamente por criar familia ou aprovar outra solicitacao.
- A consistencia de responsaveis com a familia do agregado continua na camada de aplicacao.
# Complemento V6

`ParticipanteCompra.participanteListaOrigem` e opcional. Quando presente, o participante foi gerado da preparacao da lista; quando ausente, ingressou diretamente na compra apos seu inicio. `compra` e `membroFamilia` permanecem obrigatorios, assim como `nomeSnapshot`, `papelSnapshot` e `geradoEm`. A unicidade de `(compra_id, membro_familia_id)` permanece. O indice parcial `ux_participante_compra_origem_quando_presente` garante que uma origem em `participante_lista_origem_id` seja usada no maximo uma vez quando nao nula.
