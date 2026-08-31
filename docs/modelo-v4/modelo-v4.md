# Modelo de Dominio v4

Data desta revisao: 2026-08-31

Este documento, o diagrama Mermaid e o DBML em `docs/modelo-v4` descrevem o estado completo do banco apos V1, V2, V3 e V4.

## Decisoes de persistencia

- Todas as chaves primarias e estrangeiras usam UUID.
- Todos os instantes de dominio e auditoria usam `TIMESTAMPTZ` no PostgreSQL.
- Enums conceituais em Java sao `VARCHAR` com `CHECK CONSTRAINT` no PostgreSQL.
- `usuario.senha_hash` e obrigatorio e armazena exclusivamente hash, nunca senha em texto puro.
- A consistencia de membros responsaveis com a familia do agregado permanece na camada de aplicacao; nao ha FKs compostas, triggers ou colunas redundantes.

## Entidades

### Usuario

- `id`, `nome`, `email`, `senha_hash`, `criado_em`, `atualizado_em`.
- `email` e unico; `senha_hash` possui no maximo 255 caracteres.

### Familia

- `id`, `nome`, `codigo_ingresso`, `status`, `criada_por_usuario_id`, `criada_em`, `atualizada_em`.
- `codigo_ingresso` e obrigatorio e unico; nao possui expiracao no MVP.
- Enum `status`: `ATIVA`, `INATIVA`.

### MembroFamilia

- `id`, `familia_id`, `usuario_id`, `papel`, `status`, `apelido`, `criado_em`, `atualizado_em`.
- `UNIQUE (familia_id, usuario_id)` impede vinculos repetidos; o indice unico parcial em `usuario_id` para `status = 'ATIVO'` garante no maximo uma familia ativa por usuario.
- Enums: `papel` = `ADMINISTRADOR`, `MEMBRO`; `status` = `ATIVO`, `INATIVO`.

### SolicitacaoEntradaFamilia

- `id`, `familia_id`, `solicitante_usuario_id`, `status`, `solicitada_em`, `resolvida_por_membro_familia_id`, `resolvida_em`.
- Ha no maximo uma solicitacao `PENDENTE` por par familia e usuario, por indice unico parcial.
- `PENDENTE` e `CANCELADA` exigem responsavel e instante de resolucao nulos; `APROVADA` e `REJEITADA` exigem ambos preenchidos.
- Enum `status`: `PENDENTE`, `APROVADA`, `REJEITADA`, `CANCELADA`.

### ListaCompra

- `id`, `familia_id`, `nome`, `categoria`, `estabelecimento`, `status`, `criada_por_membro_familia_id`, `criada_em`, `atualizada_em`.
- `categoria` e obrigatoria; `estabelecimento` e nullable.
- Enum `status`: `EM_PREPARACAO`, `EM_COMPRA`, `FINALIZADA`, `CANCELADA`.

### ParticipanteLista

- `id`, `lista_compra_id`, `membro_familia_id`, `entrou_em`, `saiu_em`.
- O participante esta ativo quando `saiu_em IS NULL`; `UNIQUE (lista_compra_id, membro_familia_id)` impede duplicidade.

### ItemLista

- `id`, `lista_compra_id`, `descricao`, `quantidade`, `unidade_medida`, `marca`, `observacoes`, `ordem_exibicao`, `removido_em`, `adicionado_por_membro_familia_id`, `criado_em`, `atualizado_em`.
- `removido_em` implementa remocao logica; itens ativos sao ordenados por `ordem_exibicao`, com desempate por `id` na consulta.

### Compra

- `id`, `lista_compra_id`, `iniciada_por_membro_familia_id`, `nome_lista_snapshot`, `categoria_snapshot`, `estabelecimento_snapshot`, `status`, `iniciada_em`, `finalizada_em`, `reaberta_em`, `reaberta_por_membro_familia_id`.
- `lista_compra_id` e obrigatorio e unico; a reabertura reutiliza a mesma compra e preenche seus campos em par.
- Enum `status`: `EM_ANDAMENTO`, `FINALIZADA`, `CANCELADA`.

### ParticipanteCompra

- `id`, `compra_id`, `participante_lista_origem_id`, `membro_familia_id`, `nome_snapshot`, `papel_snapshot`, `gerado_em`.
- A origem e obrigatoria e unica; somente participantes ativos geram snapshot.
- Enum `papel_snapshot`: `ADMINISTRADOR`, `MEMBRO`.

### ItemCompra

- `id`, `compra_id`, `item_lista_origem_id`, `adicionado_durante_compra`, `ordem_exibicao`, `descricao_snapshot`, `quantidade_snapshot`, `unidade_medida_snapshot`, `marca_snapshot`, `observacoes_snapshot`, `status`, `marcado_por_membro_familia_id`, `marcado_em`, `decisao_remocao`, `remocao_solicitada_por_membro_familia_id`, `remocao_solicitada_em`, `remocao_resolvida_por_membro_familia_id`, `remocao_resolvida_em`.
- A origem e nullable e unica para suportar itens criados durante a compra.
- Enums: `status` = `PENDENTE`, `NO_CARRINHO`, `REMOCAO_SOLICITADA`, `REMOVIDO`; `decisao_remocao` = `APROVADA`, `REJEITADA`.

## Regras estruturais

- Os snapshots de lista, participantes e itens sao preservados na compra.
- `adicionado_durante_compra = true` exige origem nula; quando falso, a origem e obrigatoria.
- `REMOCAO_SOLICITADA` exige solicitacao preenchida; `REMOVIDO` exige decisao `APROVADA`.
- Campos de solicitacao, resolucao e reabertura sao preenchidos em pares quando aplicavel.
- Ao aprovar uma solicitacao, as demais pendencias do solicitante sao marcadas como `CANCELADA`. Esse estado nao representa decisao administrativa e nao preenche campos de resolucao.
