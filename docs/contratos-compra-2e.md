# CONTRATOS PARA REPASSE AO FRONTEND — COMPRA 2E

> Atualização de presença operacional (V10): colocação e restauração exigem PRESENTE, inclusive em replay. As regras de remoção permanecem iguais. Consulte [o contrato atual de presença](contrato-presenca-operacional.md).

Marco Compra 2E-B, validado em 10/09/2026.

## Requests e respostas

Base: `/api/familias/{familiaId}/listas/{listaId}/compra`.

Todos os POSTs abaixo recebem `Authorization: Bearer <JWT>`, **sem body**. O ator vem exclusivamente do JWT; não enviar identificadores de solicitante ou decisor.

| Método e sufixo da base | Caso normal | Resposta |
| --- | --- | --- |
| POST `/itens/{itemCompraId}/solicitar-remocao` | NO_CARRINHO → REMOCAO_SOLICITADA | 200 + ItemCompraResponse completo |
| POST `/itens/{itemCompraId}/aprovar-remocao` | REMOCAO_SOLICITADA → REMOVIDO | 200 + ItemCompraResponse completo |
| POST `/itens/{itemCompraId}/rejeitar-remocao` | REMOCAO_SOLICITADA → NO_CARRINHO | 200 + ItemCompraResponse completo |
| GET da base | Recupera compra, participantes, itens e contexto do usuário | 200 + CompraAtivaResponse |

Exemplo de request (substituir os parâmetros):

```http
POST /api/familias/{familiaId}/listas/{listaId}/compra/itens/{itemCompraId}/solicitar-remocao
Authorization: Bearer <JWT>
Accept: application/json
```

Aprovar e rejeitar usam o mesmo formato, alterando apenas o último segmento.

## Histórico e estados

Os campos anteriores do item permanecem. Foram acrescentados `remocao` e `acoes`.

| Situação | status | remocao |
| --- | --- | --- |
| Nunca houve solicitação | PENDENTE ou NO_CARRINHO | null |
| Solicitação aguardando decisão | REMOCAO_SOLICITADA | solicitadaPor/solicitadaEm preenchidos; decisao/decididaPor/decididaEm null |
| Aprovação ou autoaprovação | REMOVIDO | decisao APROVADA; solicitante, decisor e datas preenchidos |
| Rejeição | NO_CARRINHO | decisao REJEITADA; solicitante, decisor e datas preenchidos |

`solicitadaPor` e `decididaPor` usam a referência existente:
`{ participanteCompraId, membroFamiliaId, usuarioId, nome }`.
O nome é o `ParticipanteCompra.nomeSnapshot` da mesma compra, preservado mesmo se o cadastro atual mudar. Datas são instantes ISO-8601 em UTC.

Rejeitar preserva os metadados do ciclo. Uma nova solicitação após rejeição substitui solicitante/data e limpa a decisão, decisor/data anteriores. Só o ciclo atual/mais recente é exposto.

## Capabilities e responsável

`acoes` está sempre presente em cada item:

```json
{
  "podeSolicitarRemocao": true,
  "podeDecidirRemocao": false
}
```

- `podeSolicitarRemocao`: compra EM_ANDAMENTO, usuário participante da compra e item NO_CARRINHO. Inclui item com decisão anterior REJEITADA.
- `podeDecidirRemocao`: compra EM_ANDAMENTO, usuário participante, item REMOCAO_SOLICITADA e membro atual igual a quem colocou o item no carrinho.
- Observadores, inclusive administradores não participantes, recebem ambas false.
- Administrador, criador da lista, solicitante ou iniciador da compra não recebem privilégio de decisão pelo papel.
- Fora de EM_ANDAMENTO, ambas são false.
- `contextoUsuario.participanteCompra` permanece global e inalterado; decisões são por item.

O frontend deve usar as capabilities retornadas, sem reconstruir autorização a partir de nomes ou papéis. Elas descrevem ações disponíveis agora; replays autorizados podem retornar 200 mesmo com a capability false.

Se quem colocou no carrinho inicia uma nova solicitação, há autoaprovação imediata: **200, REMOVIDO, APROVADA**, mesmo solicitante e decisor, datas preenchidas, ambas as capabilities false. Não há uma segunda etapa de aprovação.

## Replay e concorrência

| Repetição autorizada | Resultado |
| --- | --- |
| Solicitar enquanto REMOCAO_SOLICITADA | 200, preservando primeiro solicitante e instante do ciclo |
| Responsável aprova novamente REMOVIDO + APROVADA | 200, mesma auditoria |
| Responsável rejeita novamente NO_CARRINHO + REJEITADA | 200, mesma auditoria |

Repetir solicitar durante uma pendência, inclusive pelo responsável, não converte esse replay em autoaprovação. Solicitar depois da autoaprovação encontra REMOVIDO e retorna 409. Rejeitar NO_CARRINHO sem decisão REJEITADA não é replay.

As validações de participante, responsável e compra em andamento continuam valendo nos replays. Aprovar e rejeitar simultaneamente são serializados pelo bloqueio PESSIMISTIC_WRITE existente: uma decisão vence (200), a oposta recebe 409. Reconsultar GET para mostrar o estado vigente.

## Erros

O envelope atual permanece: `timestamp, status, erro, mensagem, path, campos`.

| HTTP | Situação |
| --- | --- |
| 401 | JWT ausente ou inválido |
| 403 | Não participante ou participante sem responsabilidade tentando decidir |
| 404 | Lista inexistente/incompatível com família, compra inexistente ou item inexistente/de outra compra |
| 409 | Compra fora de EM_ANDAMENTO, estado/transição incompatível, responsável inválido ou decisão oposta que perdeu a concorrência |

As validações existentes de vínculo familiar continuam aplicáveis (membro inválido recebe 403). O contrato não fornece detalhes adicionais de outra família. Para 403, o código é `ACESSO_NEGADO`; para 409, `CONFLITO_DE_ESTADO`. O frontend deve tratar status/código, sem depender do texto de mensagem.

## GET, F5 e atualização da interface

Após sucesso, o POST entrega o item completo para substituir o item local pelo mesmo `id`. GET continua sendo a fonte de verdade para recarga e reconciliação. As capabilities dependem do JWT de cada consulta.

GET recupera pendência, aprovação, rejeição e novo ciclo. **REMOVIDO continua em itens[]**, com toda a autoria do ciclo; não há exclusão física. A apresentação pode distinguir itens removidos, mas deve manter o histórico recuperável.

O mesmo mapper atende GET, início da compra, adicionar item, colocar no carrinho e os três endpoints de remoção. O formato e os valores do item coincidem para o mesmo estado e usuário.

## Respostas reais de integração

Exemplos abaixo extraídos de GET após decisão, com usuários fictícios Ana e Bia criados pelo teste HTTP. O teste verifica igualdade integral entre item retornado pelo POST, replay e item recuperado por GET. IDs e instantes pertencem à execução e não são valores fixos de contrato.

### Aprovação (POST aprovar-remocao: 200)

```json
{
    "id":  "75aacf6c-910d-4dc2-9f32-ae9f92c17bcd",
    "itemListaOrigemId":  "e227b8e2-ece8-4ea0-be2e-cbeb8e310822",
    "adicionadoDuranteCompra":  false,
    "descricao":  "Arroz",
    "quantidade":  1.000,
    "unidadeMedida":  "UNIDADE",
    "marca":  null,
    "observacoes":  null,
    "ordemExibicao":  1,
    "status":  "REMOVIDO",
    "adicionadoPor":  null,
    "adicionadoEm":  null,
    "colocadoNoCarrinhoPor":  {
                                  "participanteCompraId":  "11ec3853-678f-491c-9572-e9cc8af951a9",
                                  "membroFamiliaId":  "9699882b-0d49-4683-bc35-7b4f3ca02c76",
                                  "usuarioId":  "e2923a37-0df6-4154-82c8-9f9ccdc4893e",
                                  "nome":  "Ana"
                              },
    "colocadoNoCarrinhoEm":  "2026-09-10T13:54:15.762560Z",
    "remocao":  {
                    "solicitadaPor":  {
                                          "participanteCompraId":  "1ccf0b06-31a5-4cf9-8466-c58428b03627",
                                          "membroFamiliaId":  "712627aa-2279-410e-8911-0f68fdeb47b5",
                                          "usuarioId":  "c6597deb-2f3e-494f-8cfd-bc2eb75a979d",
                                          "nome":  "Bia"
                                      },
                    "solicitadaEm":  "2026-09-10T13:54:15.792770Z",
                    "decisao":  "APROVADA",
                    "decididaPor":  {
                                        "participanteCompraId":  "11ec3853-678f-491c-9572-e9cc8af951a9",
                                        "membroFamiliaId":  "9699882b-0d49-4683-bc35-7b4f3ca02c76",
                                        "usuarioId":  "e2923a37-0df6-4154-82c8-9f9ccdc4893e",
                                        "nome":  "Ana"
                                    },
                    "decididaEm":  "2026-09-10T13:54:15.821222Z"
                },
    "acoes":  {
                  "podeSolicitarRemocao":  false,
                  "podeDecidirRemocao":  false
              }
}
```

### Rejeição (POST rejeitar-remocao: 200)

```json
{
    "id":  "7f5a03fe-49d9-4742-a28c-45b17c11508b",
    "itemListaOrigemId":  "2ea5cc41-5b82-4776-832b-e0dae670d179",
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
                                  "participanteCompraId":  "5440f1e2-3003-425e-b4be-8f83fdd004ed",
                                  "membroFamiliaId":  "d334d5ba-e9d1-41a0-9bba-ea699df4ff1a",
                                  "usuarioId":  "bf92dc6b-0b75-4c13-9807-50b186b354da",
                                  "nome":  "Ana"
                              },
    "colocadoNoCarrinhoEm":  "2026-09-10T13:54:16.417185Z",
    "remocao":  {
                    "solicitadaPor":  {
                                          "participanteCompraId":  "e0070d96-2b1e-420c-9ac7-45dffa0c351b",
                                          "membroFamiliaId":  "2ad3bdc4-e8d3-4f78-969b-a920ae8fbcdb",
                                          "usuarioId":  "4e885fe7-093e-477e-a48d-23f4fc72b32e",
                                          "nome":  "Bia"
                                      },
                    "solicitadaEm":  "2026-09-10T13:54:16.463252Z",
                    "decisao":  "REJEITADA",
                    "decididaPor":  {
                                        "participanteCompraId":  "5440f1e2-3003-425e-b4be-8f83fdd004ed",
                                        "membroFamiliaId":  "d334d5ba-e9d1-41a0-9bba-ea699df4ff1a",
                                        "usuarioId":  "bf92dc6b-0b75-4c13-9807-50b186b354da",
                                        "nome":  "Ana"
                                    },
                    "decididaEm":  "2026-09-10T13:54:16.498406Z"
                },
    "acoes":  {
                  "podeSolicitarRemocao":  true,
                  "podeDecidirRemocao":  false
              }
}
```

## Validação e implementação

1. Primeiro: `mvn -Dtest=RemocaoItemCompraHttpIntegrationTests test` — 31 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS.
2. Depois: `mvn clean test` — **155 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS** (124 anteriores + 31 novos).
3. PostgreSQL `18-alpine` via Testcontainers; Flyway aplicou as sete migrations V1–V7; Hibernate `ddl-auto=validate`; `spring.jpa.open-in-view=false`.

Cobertura nova: solicitação, autoaprovação, aprovação/rejeição, replays e novo ciclo; observers e administradores; JWT ausente/inválido; isolamento entre compras e família/lista; estados incompatíveis; compra encerrada; decisão concorrente 200/409; GET após cada etapa; snapshots após mudança cadastral. Regressões de início/GET, itens de preparação, inclusão durante compra e colocação/replay no carrinho passaram junto à suíte anterior.

Carregamento: EntityGraph pontual dos itens inclui os membros da solicitação e resolução. A consulta carrega os participantes da compra uma vez; o mapper monta índices por participante e membro para reutilizar as referências históricas, sem consultas por item. Open-in-view continua false e não houve EAGER global.

Arquivos de produção, relativos a `02_fontes/src/main/java/com/mercadeira/api/`:

- `compra/api/CompraController.java`: três endpoints delegando aos casos de uso 2E-A existentes.
- `compra/api/CompraAtivaResponse.java`: usa mapper com contexto do usuário.
- `compra/api/ItemCompraResponse.java`: campos remocao e acoes.
- `compra/api/RemocaoItemCompraResponse.java`: auditoria agrupada.
- `compra/api/AcoesItemCompraResponse.java`: capabilities por item.
- `compra/api/ItemCompraResponseMapper.java`: mapeamento único e índices dos snapshots.
- `compra/repository/ItemCompraRepository.java`: EntityGraph ampliado.
- `api/ApiExceptionHandler.java`: decisão sem permissão → 403; responsável inválido → 409; transição inválida mantém 409.

Testes: `02_fontes/src/test/java/com/mercadeira/api/api/RemocaoItemCompraHttpIntegrationTests.java`. Testes HTTP via MockMvc, JWT real e PostgreSQL de Testcontainers, sem transação externa no teste; exercitam serialização com open-in-view desativado.

## Limitações deliberadas

Não foram implementados finalização, WebSocket, histórico de múltiplos ciclos, tabela de eventos, entrada/saída tardia, reordenação, edição de ItemCompra ou exclusão física. Não há nova migration nem alteração do frontend. As regras de domínio e os casos de uso da Compra 2E-A foram reutilizados.
