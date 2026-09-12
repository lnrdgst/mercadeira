# CONTRATOS PARA REPASSE AO FRONTEND — COMPRA 4

Marco Compra 4C: restauração de ItemCompra no carrinho. Validado em 12/09/2026.

## Endpoint e request

`POST /api/familias/{familiaId}/listas/{listaId}/compra/itens/{itemCompraId}/restaurar-no-carrinho`

Sem body. O usuário vem exclusivamente do JWT. Não enviar restaurador, participanteCompraId, membroFamiliaId, status ou timestamps.

```http
POST /api/familias/{familiaId}/listas/{listaId}/compra/itens/{itemCompraId}/restaurar-no-carrinho
Authorization: Bearer <JWT>
Accept: application/json
```

Restauração efetiva e replay autorizado retornam **200 OK + ItemCompraResponse completo**. Não há 201 nem sucesso com corpo vazio. O boolean interno `restauradoAgora` não integra o JSON.

## Autorização e significado

Qualquer ParticipanteCompra da mesma Compra, com MembroFamilia ainda ATIVO, pode restaurar enquanto a Compra estiver EM_ANDAMENTO. Administrador não participante e observador não podem. Não é necessário ser responsável anterior, solicitante, aprovador, iniciador ou administrador. A mesma autorização vale para replay.

Restaurar significa **colocar novamente o item no carrinho**: `REMOVIDO → NO_CARRINHO`. Exige remoção APROVADA com auditoria coerente. Quem restaura passa a ser o responsável operacional pela última colocação e pela decisão da próxima solicitação de remoção.

## Response, auditoria e snapshots

Os campos anteriores do ItemCompraResponse permanecem. Foram acrescentados `restauracao` e `acoes.podeRestaurarNoCarrinho`.

Quando nunca houve restauração, `restauracao` está presente com valor null. Depois:

```json
{
  "restauracao": {
    "restauradoPor": {
      "participanteCompraId": "UUID",
      "membroFamiliaId": "UUID",
      "usuarioId": "UUID",
      "nome": "Camila"
    },
    "restauradoEm": "2026-09-12T14:08:51.375738Z"
  }
}
```

A referência usa exclusivamente `ParticipanteCompra.nomeSnapshot`. Alterar Usuario.nome não altera os nomes históricos retornados. Datas são instantes ISO-8601 em UTC; a restauração usa um único instante do Clock do backend, em precisão de microssegundos.

Após restaurar:

- `colocadoNoCarrinhoPor = restauracao.restauradoPor`;
- `colocadoNoCarrinhoEm = restauracao.restauradoEm`;
- `remocao` mantém solicitante, data, decisão APROVADA, decisor e data da remoção vigente;
- `adicionadoPor` e `adicionadoEm` permanecem intactos.

A marcação anterior é substituída pela última colocação operacional. Não há campo para recuperar a marcação original. É válido ter simultaneamente `NO_CARRINHO`, `remocao.decisao = APROVADA` e `restauracao != null`.

## Capabilities por item

**O frontend NÃO deve inferir autorização. Usar `item.acoes.podeRestaurarNoCarrinho`.** Não reconstruir a regra a partir de nomes, papéis ou autoria anterior.

A nova capability é true somente com Compra EM_ANDAMENTO, participante autorizado com vínculo ativo, item REMOVIDO e aprovação coerente: solicitação/resolução preenchidas, resolução não anterior à solicitação e responsável da colocação compatível com o aprovador.

| Estado, para participante autorizado em Compra EM_ANDAMENTO | podeSolicitarRemocao | podeDecidirRemocao | podeRestaurarNoCarrinho |
| --- | --- | --- | --- |
| PENDENTE | false | false | false |
| NO_CARRINHO, inclusive após restauração ou rejeição | true | false | false |
| REMOCAO_SOLICITADA | false | true somente para responsável atual | false |
| REMOVIDO com aprovação coerente | false | false | true |
| REMOVIDO inconsistente | false | false | false |

Observadores, inclusive administradores não participantes, recebem todas false. Fora de EM_ANDAMENTO, todas ficam false. Membro inativo é recusado pela autorização da consulta. A capability representa a ação disponível agora; false após restaurar não impede um replay autorizado do estado vigente. O POST revalida as condições sob lock.

## Replay e conflitos

Replay reconhecível: `NO_CARRINHO + APROVADA + restauração coerente com a aprovação vigente`. Retorna 200 e preserva primeiro restaurador, restauradoEm, colocadoNoCarrinhoPor/Em e auditoria da remoção. Outro participante autorizado não assume autoria ao repetir a chamada.

| HTTP | Situação |
| --- | --- |
| 401 | JWT ausente ou inválido |
| 403 | Não participante, administrador não participante, observador ou vínculo familiar inativo/inválido |
| 404 | Lista ausente/incompatível com família, Compra ausente ou item inexistente/de outra Compra |
| 409 | PENDENTE, REMOCAO_SOLICITADA, NO_CARRINHO comum, NO_CARRINHO após REJEITADA, REMOVIDO inconsistente, Compra FINALIZADA/CANCELADA ou restauração incoerente |

O envelope 403/404/409 permanece: `timestamp, status, erro, mensagem, path, campos`. Códigos: `ACESSO_NEGADO`, `RECURSO_NAO_ENCONTRADO`, `CONFLITO_DE_ESTADO`. `RestauracaoItemCompraInvalidaException` é mapeada para 409. O filtro pode retornar 401 sem JSON; tratar status/código e não depender de texto de mensagem. Recursos externos ao contexto não têm detalhes expostos.

Repetir aprovação antiga depois da restauração não remove novamente o item: o responsável atual encontra transição incompatível; o responsável anterior que perdeu a responsabilidade não ganha permissão de decisão.

## GET, F5 e atualização da interface

`GET /api/familias/{familiaId}/listas/{listaId}/compra` permanece a fonte de verdade. Recupera status, auditorias, última colocação e capabilities calculadas para o JWT da consulta, inclusive após F5.

Após sucesso, substituir o item local pelo ItemCompraResponse completo do POST, usando o mesmo `id`; reconciliar por GET quando necessário. O servidor usa o mesmo mapper para GET, início, inclusão, colocação, solicitação/aprovação/rejeição de remoção e restauração. O POST consulta o estado vigente após o caso de uso, como os demais endpoints de item.

Em 409 ou mudança concorrente, reconsultar GET antes de oferecer novas ações. Removidos continuam presentes em `itens[]`; não há exclusão física.

## Novo ciclo e segunda restauração

1. Ana colocou; Bia solicitou; Ana aprovou: REMOVIDO, remoção APROVADA.
2. Camila restaura: NO_CARRINHO; restauração de Camila; marcação operacional de Camila; remoção anterior preservada.
3. Bia solicita novamente: REMOCAO_SOLICITADA; novo solicitante/data; decisão, decisor e data de resolução anteriores são limpos. Restauração de Camila permanece.
4. Somente Camila recebe `podeDecidirRemocao = true`. Ana não decide pela autoria antiga; Bia não decide por ter solicitado.
5. Se Camila rejeitar: NO_CARRINHO + REJEITADA; restauração de Camila permanece, mas chamar restaurar não é replay.
6. Se Camila aprovar: REMOVIDO + APROVADA do ciclo 2; restauração de Camila permanece até nova restauração.
7. Bia restaura: NO_CARRINHO; última restauração e última colocação passam a Bia com o mesmo novo instante; auditoria da remoção do ciclo 2 permanece.

GET recupera cada etapa. Só o ciclo atual/mais recente de remoção e a última restauração são expostos; não reconstruir ciclos históricos anteriores.

## Finalização e concorrência

Item restaurado em NO_CARRINHO permite finalizar normalmente. Após finalização, GET mantém restauração e remoção, todas as capabilities do item são false e POST restaurar retorna 409, inclusive replay. As demais regras de finalização do [Compra 3](contratos-compra-3.md) permanecem.

A restauração reutiliza os locks **Compra PESSIMISTIC_WRITE → validação EM_ANDAMENTO → ItemCompra PESSIMISTIC_WRITE**. Finalização mantém ListaCompra → Compra.

- Duas restaurações: uma efetiva a transição, a outra reconhece replay; primeiro autor/instante permanecem.
- Restauração primeiro versus finalização: finalização aguarda, observa NO_CARRINHO e pode finalizar.
- Finalização primeiro: restauração aguarda, encontra FINALIZADA e recebe 409; item permanece REMOVIDO.
- Solicitação de remoção antes da restauração encontra REMOVIDO e recebe 409.
- Restauração primeiro permite que solicitação posterior abra novo ciclo.

Não há congelamento da tela nem precondição de versão. A concorrência principal permanece coberta pelos testes reais do Compra 4B, preservados neste marco.

## Limitação deliberada entre ciclos

O endpoint oferece idempotência/replay do **ESTADO VIGENTE**. Uma chamada antiga de restauração que chegue depois de um novo ciclo aprovado pode ser indistinguível de uma nova intenção de restaurar, efetivando outra restauração.

Não existem versionamento do comando, id do ciclo ou idempotency-key. Não há histórico completo de ciclos, tabela de eventos, recuperação da marcação original ou mecanismo novo de deduplicação. Não implementar inferências de histórico no frontend.

## Exemplo real de integração

Item completo extraído de GET no teste HTTP após restauração por Camila. O cadastro de Camila foi alterado antes da restauração, mas o nomeSnapshot permanece. O teste compara integralmente o POST, o replay por Bia e o item de GET. IDs e datas pertencem à execução de 12/09/2026 e não são constantes do contrato.

```json
{
    "id":  "ef5f46dc-818d-4a06-9de6-2c02003616cb",
    "itemListaOrigemId":  "7fbfb25a-c843-4834-bf6f-7f2cc70d103a",
    "adicionadoDuranteCompra":  false,
    "descricao":  "Arroz",
    "quantidade":  1.000,
    "unidadeMedida":  "UNIDADE",
    "marca":  null,
    "observacoes":  null,
    "ordemExibicao":  1,
    "status":  "NO_CARRINHO",
    "adicionadoPor":  null,
    "adicionadoEm":  null,
    "colocadoNoCarrinhoPor":  {
                                  "participanteCompraId":  "cec6c7ca-da65-4e47-bc47-422f104ea7c9",
                                  "membroFamiliaId":  "91710e8d-998a-40d9-a249-6bfa89cc7198",
                                  "usuarioId":  "731cb8dd-d6f3-4a83-b7a9-59b98529b71d",
                                  "nome":  "Camila"
                              },
    "colocadoNoCarrinhoEm":  "2026-09-12T14:08:51.375738Z",
    "remocao":  {
                    "solicitadaPor":  {
                                          "participanteCompraId":  "31560c42-7322-4245-99bf-faa19cc7a097",
                                          "membroFamiliaId":  "57f22980-7617-499e-855e-dce977b816ce",
                                          "usuarioId":  "70cf408f-4c0f-4575-a408-5bc48ce805bb",
                                          "nome":  "Bia"
                                      },
                    "solicitadaEm":  "2026-09-12T14:08:51.323380Z",
                    "decisao":  "APROVADA",
                    "decididaPor":  {
                                        "participanteCompraId":  "858e8771-eaa6-4dbf-85ac-77148502afca",
                                        "membroFamiliaId":  "301ac765-105c-4eac-ac97-948bb990eb19",
                                        "usuarioId":  "60e2c2ff-27b4-4f8d-98c2-b07736fc42aa",
                                        "nome":  "Ana"
                                    },
                    "decididaEm":  "2026-09-12T14:08:51.348843Z"
                },
    "restauracao":  {
                        "restauradoPor":  {
                                              "participanteCompraId":  "cec6c7ca-da65-4e47-bc47-422f104ea7c9",
                                              "membroFamiliaId":  "91710e8d-998a-40d9-a249-6bfa89cc7198",
                                              "usuarioId":  "731cb8dd-d6f3-4a83-b7a9-59b98529b71d",
                                              "nome":  "Camila"
                                          },
                        "restauradoEm":  "2026-09-12T14:08:51.375738Z"
                    },
    "acoes":  {
                  "podeSolicitarRemocao":  true,
                  "podeDecidirRemocao":  false,
                  "podeRestaurarNoCarrinho":  false
              }
}
```

## Validação e implementação

- Novos testes HTTP: `mvn -Dtest=RestauracaoItemCompraHttpIntegrationTests test` — **25 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS**.
- Regressões relacionadas: `mvn -Dtest=RemocaoItemCompraHttpIntegrationTests,FinalizarCompraIntegrationTests,RestaurarItemNoCarrinhoIntegrationTests,RestauracaoItemCompraTest,ColocarItemNoCarrinhoApplicationTests,ConsultarCompraDaListaApplicationTests test` — **107 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS**.
- Suíte completa: `mvn clean test` — **305 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS**, código de saída 0. São os 280 anteriores mais 25 novos.
- PostgreSQL `18-alpine` via Testcontainers, Flyway V1–V9, Hibernate `ddl-auto=validate`, `open-in-view=false`.

A suíte completa registrou demora no encerramento da JVM de testes: o Surefire encerrou o processo após 30 segundos de shutdown. Os relatórios XML confirmam os 305 testes aprovados, e o Maven terminou com BUILD SUCCESS e código 0. Esse aviso de encerramento também havia ocorrido na validação do Compra 4B.

Testes HTTP via MockMvc e JWT real, sem transação externa envolvendo o teste. Cobertura: autorização e replay; 401/403/404/409; inconsistência de auditoria/responsável; capacidades por usuário/estado; snapshot; igualdade integral POST/replay/GET; preservação de inclusão; autoaprovação; novo ciclo com aprovação/rejeição; segunda restauração; finalização com item removido/restaurado; aprovação antiga sem remover novamente.

Arquivos de produção, relativos a `02_fontes/src/main/java/com/mercadeira/api/`:

- `compra/api/CompraController.java`: endpoint delegando ao caso de uso existente e ao mapper centralizado.
- `compra/api/ItemCompraResponse.java`: campo restauracao.
- `compra/api/RestauracaoItemCompraResponse.java`: restauradoPor e restauradoEm.
- `compra/api/AcoesItemCompraResponse.java`: podeRestaurarNoCarrinho.
- `compra/api/ItemCompraResponseMapper.java`: lookup dos snapshots e capability.
- `api/ApiExceptionHandler.java`: exceção de restauração → 409.

O mapper usa somente o ID da associação LAZY para lookup no mapa dos ParticipanteCompra carregados uma vez pela consulta existente. Não houve query adicional por item, ampliação de EntityGraph ou EAGER global.

Teste novo: `02_fontes/src/test/java/com/mercadeira/api/api/RestauracaoItemCompraHttpIntegrationTests.java`. Em `FinalizarCompraIntegrationTests.java`, as duas variações do teste de preservação de removidos foram atualizadas para esperar podeRestaurarNoCarrinho true antes e false depois de finalizar, mantendo a comparação integral dos demais campos.

Sem alteração das regras de domínio/aplicação do Compra 4B, frontend ou migrations V1–V9. Não há V10, commit ou push neste marco.
