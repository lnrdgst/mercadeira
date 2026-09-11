# CONTRATOS PARA REPASSE AO FRONTEND — COMPRA 3

Marco Compra 3C: finalização da Compra. Validado em 11/09/2026.

## Endpoint e request

`POST /api/familias/{familiaId}/listas/{listaId}/compra/finalizar`

Sem body. Usuário exclusivamente pelo JWT. Não enviar autor, participante, membro ou data de finalização.

```http
POST /api/familias/{familiaId}/listas/{listaId}/compra/finalizar
Authorization: Bearer <JWT>
Accept: application/json
```

Primeira finalização e replay retornam **200 OK**, com a Compra completa. Não há resposta 201 nem corpo vazio em caso de sucesso.

## Autorização

Pode finalizar qualquer ParticipanteCompra da mesma Compra cujo MembroFamilia ainda esteja ATIVO. Não é necessário ser iniciador, criador da lista ou administrador.

Observador e administrador não participante recebem 403. As mesmas verificações de autorização valem para replay. Membro inativo recebe 403, inclusive se participou do snapshot original.

## Estados e transação

A primeira finalização exige Compra EM_ANDAMENTO e ListaCompra EM_COMPRA.

Na mesma transação:
- Compra passa a FINALIZADA, com autor/data;
- ListaCompra passa a FINALIZADA;
- ListaCompra.atualizadaEm recebe o mesmo instante;
- os ItemCompra e suas auditorias permanecem intactos.

| Estado do item | Pode finalizar? | Resultado |
| --- | --- | --- |
| PENDENTE | Sim | Continua PENDENTE; representa item não comprado |
| NO_CARRINHO | Sim | Continua NO_CARRINHO; representa item comprado/concluído neste domínio |
| REMOVIDO | Sim | Continua REMOVIDO, com auditoria e presente em itens[] |
| REMOCAO_SOLICITADA | Não | 409; usuário precisa resolver a decisão pendente |

Todos os itens REMOVIDO também permitem finalizar. Compra sem itens é inconsistência e recebe 409. A finalização não aprova ou rejeita remoções implicitamente.

Compra CANCELADA, ListaCompra CANCELADA e combinações inconsistentes não podem finalizar. Não há correção silenciosa nem gravação parcial.

## Response e compatibilidade

O DTO interno foi renomeado de CompraAtivaResponse para **CompraResponse**. Rotas e campos JSON anteriores foram preservados. Os acréscimos são:

- `finalizadaPor`: referência de participante da Compra;
- `finalizadaEm`: instante ISO-8601 em UTC;
- `contextoUsuario.podeFinalizarCompra`: boolean calculado pelo backend.

Antes da primeira finalização:

```json
{
  "finalizadaPor": null,
  "finalizadaEm": null
}
```

Após finalização, `finalizadaPor` possui `participanteCompraId`, `membroFamiliaId`, `usuarioId` e `nome`. O nome vem de **ParticipanteCompra.nomeSnapshot**, mesmo se o cadastro atual mudar. O instante é produzido pelo backend em precisão de microssegundos, compatível com sua persistência.

## Capability

`contextoUsuario.podeFinalizarCompra` é true quando:

- usuário participa da Compra e possui vínculo familiar ativo;
- Compra está EM_ANDAMENTO e ListaCompra está EM_COMPRA;
- existe ao menos um ItemCompra;
- não existe ItemCompra REMOCAO_SOLICITADA.

PENDENTE não bloqueia. Após finalizar, essa capability é false. Observadores autorizados a consultar recebem false; membros inativos são recusados pela autorização do GET.

O frontend deve usar a capability e revalidar o estado a partir das respostas. O POST sempre valida as condições novamente sob lock; um botão habilitado anteriormente não garante que a operação ainda seja possível.

## Replay

Se Compra e ListaCompra já estão FINALIZADA, com autoria/data coerentes e itens válidos, repetir o POST retorna 200.

O replay preserva **a primeira autoria efetiva**, finalizadaEm e ListaCompra.atualizadaEm. Outro participante autorizado pode repetir a chamada sem assumir a autoria. A capability false após finalização não impede esse replay.

Não são replay: estados divergentes entre compra/lista, cancelamento, auditoria inconsistente ou dados estruturalmente inválidos.

## Concorrência

Finalização adquire locks na ordem **ListaCompra → Compra**. As mutações de itens seguem **Compra → ItemCompra**; inclusão já utiliza o lock da Compra.

- Duas finalizações: uma efetiva a transição; a outra retorna replay, preservando autor/data.
- Inclusão ou colocação no carrinho primeiro: a finalização aguarda e observa o resultado confirmado.
- Finalização primeiro: a mutação aguarda, encontra FINALIZADA e recebe 409.
- Solicitação de remoção primeiro: a finalização observa REMOCAO_SOLICITADA e recebe 409.
- Falha durante a transação: alterações de Compra e ListaCompra são revertidas juntas.

A revisão visual não congela a compra. A confirmação encerra o estado vigente quando o backend adquire os locks. Não há precondição de versão da tela neste contrato.

## Erros

| HTTP | Situação |
| --- | --- |
| 401 | JWT ausente/inválido |
| 403 | Não participante autorizado ou membro familiar inativo |
| 404 | Lista inexistente/incompatível com família ou compra não encontrada no contexto |
| 409 | Estados incompatíveis, remoção pendente, compra vazia ou inconsistência |

403/404/409 preservam o envelope centralizado: `timestamp, status, erro, mensagem, path, campos`. Códigos: `ACESSO_NEGADO`, `RECURSO_NAO_ENCONTRADO`, `CONFLITO_DE_ESTADO`. O filtro de segurança pode retornar 401 sem body; não presumir JSON em toda resposta de erro.

## GET, F5 e mutabilidade

`GET /api/familias/{familiaId}/listas/{listaId}/compra` permanece a fonte de verdade e retorna **200 para Compra FINALIZADA**, respeitando o acesso familiar existente.

Retorna autor/data, participantes, todos os itens e auditorias. PENDENTE e REMOVIDO permanecem recuperáveis. As capabilities de item `podeSolicitarRemocao` e `podeDecidirRemocao` ficam false.

Depois de finalizar, adicionar item, colocar no carrinho e solicitar/aprovar/rejeitar remoção retornam conflito, inclusive tentativas de replay dessas mutações.

O frontend pode substituir a compra local pelo response completo do POST e reconciliar por GET. A lista associada também estará FINALIZADA; atualizar sua representação local ou reconsultá-la.

## Revisão como UX

Não há estado EM_REVISAO. A interface pode apresentar uma revisão com contagens e grupos de itens antes da confirmação.

Informar que itens PENDENTE serão registrados como não comprados. Havendo REMOCAO_SOLICITADA, apresentar o impedimento. A confirmação visual não acrescenta body ao POST.

## Exemplo real antes da finalizacao

Recorte de outro fixture da mesma suite: compra em andamento apos rejeitar uma remocao. O item voltou a NO_CARRINHO, nao ha decisao pendente e a capability permite finalizar.

```json
{
    "status":  "EM_ANDAMENTO",
    "finalizadaPor":  null,
    "finalizadaEm":  null,
    "contextoUsuario":  {
                            "participanteCompra":  true,
                            "podeFinalizarCompra":  true
                        }
}
```

## Exemplo real de integração

Response completo obtido em teste com usuários fictícios. Bia, participante não administradora, finaliza uma compra com item PENDENTE. Seu cadastro é alterado depois da criação dos snapshots; o response preserva o nome histórico. O teste confirma igualdade entre POST inicial, replay por outro participante e GET.

```json
{
    "id":  "bf1509a2-d921-4fc4-b3df-bd8071e8ed3b",
    "listaId":  "193f3522-15a9-4b45-9d44-bc7f88476920",
    "nomeLista":  "Lista",
    "categoria":  "OUTROS",
    "estabelecimento":  null,
    "status":  "FINALIZADA",
    "iniciadaEm":  "2026-09-11T13:02:05.774416Z",
    "finalizadaPor":  {
                          "participanteCompraId":  "2c854c13-3c69-498e-9a0f-3dbf44febd4c",
                          "membroFamiliaId":  "48b6987a-c6c2-4e7e-831c-7c0db8f30231",
                          "usuarioId":  "3a598962-f81d-434c-993a-48e6c5edfed8",
                          "nome":  "Bia"
                      },
    "finalizadaEm":  "2026-09-11T13:02:06.045312Z",
    "participantes":  [
                          {
                              "id":  "1b92f017-2f65-4737-ba63-30b62583dd8f",
                              "membroFamiliaId":  "63c1cf27-36d1-48b2-aa39-77e2be695e6b",
                              "usuarioId":  "03ecd83c-d013-4da1-90ec-ac24c1542d16",
                              "nome":  "Ana",
                              "papel":  "ADMINISTRADOR",
                              "geradoEm":  "2026-09-11T13:02:05.774416Z"
                          },
                          {
                              "id":  "2c854c13-3c69-498e-9a0f-3dbf44febd4c",
                              "membroFamiliaId":  "48b6987a-c6c2-4e7e-831c-7c0db8f30231",
                              "usuarioId":  "3a598962-f81d-434c-993a-48e6c5edfed8",
                              "nome":  "Bia",
                              "papel":  "MEMBRO",
                              "geradoEm":  "2026-09-11T13:02:05.774416Z"
                          },
                          {
                              "id":  "84449ff3-2db2-40a6-a505-e29cd1529aaf",
                              "membroFamiliaId":  "9a0705f9-8a65-4f91-966b-7bcf2dddc20d",
                              "usuarioId":  "7b65305f-3e50-4ae8-a9b1-e7e6a1c1f442",
                              "nome":  "Caio",
                              "papel":  "MEMBRO",
                              "geradoEm":  "2026-09-11T13:02:05.774416Z"
                          }
                      ],
    "itens":  [
                  {
                      "id":  "17e6918e-b58d-4bdc-9047-6b1d2304cd93",
                      "itemListaOrigemId":  "65180c58-c8bc-414f-96fe-80b63765d4ae",
                      "adicionadoDuranteCompra":  false,
                      "descricao":  "Arroz",
                      "quantidade":  1.000,
                      "unidadeMedida":  "UNIDADE",
                      "marca":  null,
                      "observacoes":  null,
                      "ordemExibicao":  1,
                      "status":  "PENDENTE",
                      "adicionadoPor":  null,
                      "adicionadoEm":  null,
                      "colocadoNoCarrinhoPor":  null,
                      "colocadoNoCarrinhoEm":  null,
                      "remocao":  null,
                      "acoes":  {
                                    "podeSolicitarRemocao":  false,
                                    "podeDecidirRemocao":  false
                                }
                  }
              ],
    "contextoUsuario":  {
                            "participanteCompra":  true,
                            "podeFinalizarCompra":  false
                        }
}
```

IDs e datas pertencem à execução do teste e não são constantes de contrato.

## Validação

1. Testes específicos: `mvn -Dtest=FinalizacaoCompraTest,FinalizarCompraIntegrationTests test` — **36 testes**, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS.
2. Suíte completa: `mvn clean test` — **222 testes**, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS. São os 186 anteriores mais 36 novos (28 de integração e 8 de domínio).
3. PostgreSQL **18-alpine**, Flyway **V1–V8**, Hibernate **ddl-auto=validate**, open-in-view=false.

Cobertura: autorização, JWT/contexto, PENDENTE, NO_CARRINHO + REMOVIDO e todos REMOVIDO; remoção pendente e compra vazia; inconsistências e cancelamento; replay de outro participante; auditoria histórica; GET/F5; mutações bloqueadas após encerrar; falha na gravação da lista com rollback da compra; duas finalizações concorrentes; finalização versus inclusão/carrinho/solicitação de remoção em ambas as ordens.

Os testes HTTP usam MockMvc, JWT e PostgreSQL real via Testcontainers, com open-in-view=false e sem transação externa envolvendo o teste inteiro. Os testes concorrentes coordenam transações e confirmam a espera no servidor com pg_blocking_pids.

## Limitações deliberadas

Sem reabertura, WebSocket, cancelamento operacional, estado de revisão, confirmação financeira, histórico de múltiplos ciclos ou nova migration. V1–V8 permanecem intactas. O histórico de remoção continua representando o ciclo atual/mais recente conforme Compra 2E. Nenhuma alteração de frontend foi feita neste marco.
