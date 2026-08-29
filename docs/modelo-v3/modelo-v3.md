# Modelo de Dominio v3

Data desta revisao: 2026-08-29

Este documento, o diagrama Mermaid e o DBML em `docs/modelo-v3` formam a fonte de verdade do modelo v3. O estado descrito inclui integralmente a V1, a V2 e o complemento da migration V3.

## Decisoes de persistencia

- Todas as chaves primarias e estrangeiras usam UUID.
- Todos os instantes de dominio e auditoria usam `TIMESTAMPTZ` no PostgreSQL.
- Enums conceituais em Java sao `VARCHAR` com `CHECK CONSTRAINT` no PostgreSQL.
- Senhas nao sao persistidas em texto puro. `usuario.senha_hash` armazena exclusivamente o hash gerado pela futura camada de aplicacao.
- A consistencia de que membros responsaveis pertencem a familia do agregado sera garantida pela futura camada de aplicacao. A V2 nao introduz FKs compostas, triggers ou colunas redundantes.

## Entidades

### Usuario

- `id`, `nome`, `email`, `senha_hash`, `criado_em`, `atualizado_em`
- `email` e unico.
- `senha_hash` e obrigatorio, possui tamanho maximo de 255 caracteres e nao representa senha em texto puro.

### Familia

- `id`, `nome`, `codigo_ingresso`, `status`, `criada_por_usuario_id`, `criada_em`, `atualizada_em`
- `codigo_ingresso` e obrigatorio e unico; nao possui expiracao no MVP.

Enum `status`: `ATIVA`, `INATIVA`.

### MembroFamilia

- `id`, `familia_id`, `usuario_id`, `papel`, `status`, `apelido`, `criado_em`, `atualizado_em`
- `UNIQUE (familia_id, usuario_id)` impede vinculos repetidos.
- Indice unico parcial em `usuario_id` para `status = 'ATIVO'` garante no maximo uma familia ativa por usuario.

Enums: `papel` = `ADMINISTRADOR`, `MEMBRO`; `status` = `ATIVO`, `INATIVO`.

### SolicitacaoEntradaFamilia

- `id`, `familia_id`, `solicitante_usuario_id`, `status`, `solicitada_em`, `resolvida_por_membro_familia_id`, `resolvida_em`
- Uma solicitacao pendente e unica por par familia e usuario.
- Os campos de resolucao sao nullable enquanto a solicitacao estiver pendente.

Enum `status`: `PENDENTE`, `APROVADA`, `REJEITADA`.

### ListaCompra

- `id`, `familia_id`, `nome`, `categoria`, `estabelecimento`, `status`, `criada_por_membro_familia_id`, `criada_em`, `atualizada_em`
- `categoria` e obrigatoria; `estabelecimento` e nullable.
- Na reabertura excepcional, a lista retorna a `EM_COMPRA`.

Enum `status`: `EM_PREPARACAO`, `EM_COMPRA`, `FINALIZADA`, `CANCELADA`.

### ParticipanteLista

- `id`, `lista_compra_id`, `membro_familia_id`, `entrou_em`, `saiu_em`
- O participante esta ativo quando `saiu_em IS NULL`.
- `UNIQUE (lista_compra_id, membro_familia_id)` impede duplicidade de participacao.

### ItemLista

- `id`, `lista_compra_id`, `descricao`, `quantidade`, `unidade_medida`, `marca`, `observacoes`, `ordem_exibicao`, `removido_em`, `adicionado_por_membro_familia_id`, `criado_em`, `atualizado_em`
- `removido_em` implementa remocao logica.
- Itens ativos sao ordenados por `ordem_exibicao`; empates serao desempatados por `id` na aplicacao ou consulta.

### Compra

- `id`, `lista_compra_id`, `iniciada_por_membro_familia_id`, `nome_lista_snapshot`, `categoria_snapshot`, `estabelecimento_snapshot`, `status`, `iniciada_em`, `finalizada_em`, `reaberta_em`, `reaberta_por_membro_familia_id`
- `lista_compra_id` e obrigatorio e unico: uma lista origina zero a uma compra.
- A reabertura reutiliza a mesma compra, e seus campos sao preenchidos em par.

Enum `status`: `EM_ANDAMENTO`, `FINALIZADA`, `CANCELADA`.

### ParticipanteCompra

- `id`, `compra_id`, `participante_lista_origem_id`, `membro_familia_id`, `nome_snapshot`, `papel_snapshot`, `gerado_em`
- A origem e obrigatoria e unica; somente participantes ativos da lista geram snapshot no inicio da compra.

Enum `papel_snapshot`: `ADMINISTRADOR`, `MEMBRO`.

### ItemCompra

- `id`, `compra_id`, `item_lista_origem_id`, `adicionado_durante_compra`, `ordem_exibicao`
- `descricao_snapshot`, `quantidade_snapshot`, `unidade_medida_snapshot`, `marca_snapshot`, `observacoes_snapshot`
- `status`, `marcado_por_membro_familia_id`, `marcado_em`
- `decisao_remocao`, `remocao_solicitada_por_membro_familia_id`, `remocao_solicitada_em`, `remocao_resolvida_por_membro_familia_id`, `remocao_resolvida_em`
- A origem e nullable e unica para suportar itens criados diretamente durante a compra.

Enums: `status` = `PENDENTE`, `NO_CARRINHO`, `REMOCAO_SOLICITADA`, `REMOVIDO`; `decisao_remocao` = `APROVADA`, `REJEITADA`.

## Regras estruturais

- Os snapshots de lista, participantes e itens sao preservados na compra.
- `adicionado_durante_compra = true` exige origem nula; quando falso, a origem e obrigatoria.
- `REMOCAO_SOLICITADA` exige solicitacao preenchida; `REMOVIDO` exige decisao `APROVADA`.
- Campos de solicitacao, resolucao e reabertura sao preenchidos em pares quando aplicavel.
