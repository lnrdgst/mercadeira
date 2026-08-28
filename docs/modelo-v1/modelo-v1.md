# Modelo de Dominio v1

Data desta revisao: 2026-08-27

Este documento, o diagrama Mermaid e o DBML em `docs/modelo-v1` formam a fonte de verdade do modelo v1. Esta revisao corrige a nomenclatura, os relacionamentos e os snapshots; nenhuma migration ou classe Java foi criada.

## Decisoes de persistencia

- Todas as chaves primarias e estrangeiras usam UUID.
- Todos os instantes de dominio e auditoria usam `TIMESTAMPTZ` no PostgreSQL.
- Enums permanecem conceituais em Java e, no PostgreSQL, sao representados por `VARCHAR` com `CHECK CONSTRAINT`; nao sao usados tipos `ENUM` nativos.

## Entidades

### Usuario

Pessoa identificada na aplicacao.

- `id`, `nome`, `email`, `criado_em`, `atualizado_em`
- `email` e unico.

### Familia

Nucleo colaborativo que agrega membros e listas de compra.

- `id`, `nome`, `criada_por_usuario_id`, `criada_em`, `atualizada_em`

### MembroFamilia

Vinculo entre um usuario e uma familia. Substitui a entidade generica `Membro`.

- `id`, `familia_id`, `usuario_id`, `papel`, `status`, `apelido`, `criado_em`, `atualizado_em`
- `UNIQUE (familia_id, usuario_id)` impede vinculos repetidos na mesma familia.
- Indice unico parcial PostgreSQL: `UNIQUE (usuario_id) WHERE status = 'ATIVO'` garante, no MVP, no maximo uma familia ativa por usuario.

Enums:

- `papel`: `ADMINISTRADOR`, `MEMBRO`
- `status`: `ATIVO`, `INATIVO`

### SolicitacaoEntradaFamilia

Solicitacao de um usuario para ingressar em uma familia.

- `id`, `familia_id`, `solicitante_usuario_id`, `status`, `solicitada_em`, `resolvida_por_membro_familia_id`, `resolvida_em`
- `resolvida_por_membro_familia_id` e `resolvida_em` sao nullable enquanto a solicitacao estiver pendente.

Enum:

- `status`: `PENDENTE`, `APROVADA`, `REJEITADA`

### ListaCompra

Agregado de preparacao da compra, pertencente a uma familia. Substitui a entidade generica `Lista`.

- `id`, `familia_id`, `nome`, `categoria`, `estabelecimento`, `criada_por_membro_familia_id`, `criada_em`, `atualizada_em`
- `categoria` e obrigatoria; `estabelecimento` e nullable.

### ParticipanteLista

Vinculo entre uma lista e um membro da familia.

- `id`, `lista_compra_id`, `membro_familia_id`, `entrou_em`, `saiu_em`
- `saiu_em` e nullable; participante ativo e aquele para o qual `saiu_em IS NULL`.
- Nao existe `pode_editar`: todo participante ativo pode colaborar no MVP.
- `UNIQUE (lista_compra_id, membro_familia_id)` impede duplicidade de participacao na lista.

### ItemLista

Item planejado na lista compartilhada.

- `id`, `lista_compra_id`, `descricao`, `quantidade`, `unidade_medida`, `marca`, `observacoes`, `adicionado_por_membro_familia_id`, `criado_em`, `atualizado_em`

### Compra

Execucao operacional de uma lista de compra.

- `id`, `lista_compra_id`, `iniciada_por_membro_familia_id`, `nome_lista_snapshot`, `categoria_snapshot`, `estabelecimento_snapshot`, `status`, `iniciada_em`, `finalizada_em`
- `lista_compra_id` e obrigatorio e unico: uma `ListaCompra` origina de zero a uma `Compra`.
- A reabertura reutiliza a mesma `Compra`.
- `nome_lista_snapshot` e `categoria_snapshot` sao obrigatorios; `estabelecimento_snapshot` e nullable.

Enum:

- `status`: `EM_ANDAMENTO`, `FINALIZADA`, `CANCELADA`

### ParticipanteCompra

Snapshot de um participante ativo da lista no inicio da compra.

- `id`, `compra_id`, `participante_lista_origem_id`, `membro_familia_id`, `nome_snapshot`, `papel_snapshot`, `gerado_em`
- `participante_lista_origem_id` e obrigatorio e unico: cada participante da lista gera no maximo um snapshot de compra.
- `nome_snapshot` e `papel_snapshot` sao obrigatorios e preservam, respectivamente, o nome do usuario e o papel do membro da familia na abertura da compra.
- Ao iniciar uma compra, somente `ParticipanteLista` com `saiu_em IS NULL` gera `ParticipanteCompra`.
- `UNIQUE (compra_id, membro_familia_id)` impede duplicidade de membro na compra.

### ItemCompra

Snapshot operacional de um item na compra, inclusive itens criados diretamente durante a execucao.

- `id`, `compra_id`, `item_lista_origem_id`, `adicionado_durante_compra`
- `descricao_snapshot`, `quantidade_snapshot`, `unidade_medida_snapshot`, `marca_snapshot`, `observacoes_snapshot`
- `status`, `marcado_por_membro_familia_id`, `marcado_em`
- `decisao_remocao`, `remocao_solicitada_por_membro_familia_id`, `remocao_solicitada_em`, `remocao_resolvida_por_membro_familia_id`, `remocao_resolvida_em`
- `item_lista_origem_id` e nullable e unico. Quando o item vier da preparacao, referencia o `ItemLista` de origem; quando for adicionado durante a compra, `adicionado_durante_compra = true` e `item_lista_origem_id IS NULL`.

Enums:

- `status`: `PENDENTE`, `NO_CARRINHO`, `REMOCAO_SOLICITADA`, `REMOVIDO`
- `decisao_remocao`: `APROVADA`, `REJEITADA`

Regra de remocao:

```text
NO_CARRINHO -> REMOCAO_SOLICITADA
APROVADA   -> REMOVIDO
REJEITADA  -> NO_CARRINHO
```

Uma solicitacao de remocao somente pode ser criada para item em `NO_CARRINHO`. Nao existe `status_anterior_remocao`.

## Cardinalidades principais

- `Usuario` 1 -> 0..N `MembroFamilia`; no MVP, no maximo um desses vinculos pode estar `ATIVO`.
- `Familia` 1 -> 0..N `MembroFamilia`, `SolicitacaoEntradaFamilia` e `ListaCompra`.
- `ListaCompra` 1 -> 0..N `ParticipanteLista` e `ItemLista`.
- `ListaCompra` 1 -> 0..1 `Compra`.
- `Compra` preserva `nome`, `categoria` e `estabelecimento` da `ListaCompra` no instante de abertura.
- `Compra` 1 -> 0..N `ParticipanteCompra` e `ItemCompra`.
- `ParticipanteLista` 1 -> 0..1 `ParticipanteCompra`, que preserva o nome e o papel do participante.
- `ItemLista` 1 -> 0..1 `ItemCompra`; o vinculo inverso e opcional para suportar item adicionado durante a compra.

## Regras estruturais para a futura migration

- FKs, `NOT NULL`, `UNIQUE`, indices e `CHECK CONSTRAINT` seguem o DBML.
- `status = REMOCAO_SOLICITADA` exige solicitacao de remocao preenchida.
- `status = REMOVIDO` exige `decisao_remocao = APROVADA`.
- `decisao_remocao` somente existe se houver solicitacao de remocao.
- Cada instante de solicitacao ou resolucao exige o respectivo membro responsavel.
- `adicionado_durante_compra = true` exige `item_lista_origem_id IS NULL`; quando for `false`, a origem e obrigatoria.

## Fora do escopo desta revisao

- migration Flyway
- entidades JPA
- repositories, services, controllers e DTOs
- regras de negocio Java
