# Presença operacional na Compra

## Responsabilidade operacional e solicitações (Issue #14)

A V11 evolui o contrato. Responsabilidade operacional pertence a um
`ParticipanteCompra` e somente ela decide pedidos de entrada presencial. Ela não
concede poder administrativo, de finalização ou de decisão sobre remoção de item.
`ParticipanteCompra` continua sendo participação histórica e o enum físico continua
restrito a `NAO_INFORMADA`, `PRESENTE` e `NAO_PRESENTE`.

`POST /minha-presenca/solicitacoes` é o comando de entrada. Se não houver nenhum
PRESENTE, o solicitante entra imediatamente, torna-se responsável e abre um ciclo
operacional. Caso contrário, é criado (ou retornado em replay) um pedido PENDENTE;
o estado físico anterior não muda. O responsável usa
`POST /solicitacoes-presenca/{id}/aprovar` ou `/rejeitar`; o próprio solicitante
pode usar `POST /minha-presenca/solicitacoes/{id}/cancelar`.

`PUT /minha-presenca` aceita somente `NAO_PRESENTE` como declaração de saída.
`PRESENTE` recebe 409 com orientação para o novo fluxo; ele nunca é reinterpretado
silenciosamente. A saída do responsável escolhe de forma atômica o PRESENTE elegível
mais antigo (`presencaAlteradaEm`, depois ID). Sem sucessor, o ciclo é encerrado e
os pedidos pendentes são cancelados com `SEM_PRESENTES`.

Na V12, outro participante `PRESENTE` solicita a transferência por
`POST /responsabilidade-operacional/solicitacoes`. O responsável atual decide em
`POST /solicitacoes-responsabilidade/{id}/aprovar` ou `/rejeitar`, e o solicitante
pode cancelar por `POST /responsabilidade-operacional/solicitacoes/{id}/cancelar`.
A solicitação registra ciclo e revisão da responsabilidade, portanto uma decisão
atrasada não pode alterar um ciclo ou responsável diferente. O endpoint legado de
reassunção direta recebe 409; não há tomada por polling, offline, heartbeat,
administrador familiar ou participante remoto. Se o único responsável desaparecer
sem declarar saída, esta limitação permanece no MVP.

GET e respostas de comando carregam `responsabilidadeOperacional`, a solicitação
própria/última decisão, pendências visíveis ao responsável e capabilities explícitas:
`podeSolicitarPresenca`, `podeCancelarSolicitacaoPresenca`, `podeDeclararSaida`,
`podeSolicitarResponsabilidade`, `podeCancelarSolicitacaoResponsabilidade`,
`precisaEstarPresenteParaFinalizar`, `pedido.acoes.podeDecidirPresenca` e
`pedidoResponsabilidade.acoes.podeDecidirResponsabilidade`. Eles são um
snapshot para F5, polling e reconexão; GET nunca atribui autoridade.

A V11 preserva presenças V10. Para Compra em andamento, prioriza o iniciador ainda
PRESENTE e elegível; caso contrário, escolhe o PRESENTE elegível mais antigo. Compra
finalizada não recebe responsável histórico inventado. A finalização cancela pedidos
PENDENTES com `COMPRA_FINALIZADA`, sem bloquear a regra existente de finalizar.

A V12 acrescenta a entidade distinta `SolicitacaoResponsabilidadeOperacional`, com
estados `PENDENTE`, `APROVADA`, `REJEITADA` e `CANCELADA`. Ela guarda solicitante,
responsável atual, ciclo e revisão, sem contaminar o enum físico de presença.

Backend da Issue #13. Frontend e validação integrada serão entregues separadamente.

## Conceito e persistência

ParticipanteCompra continua sendo participação e contexto histórico. Presença é declaração do próprio participante de que está executando presencialmente a Compra; não comprova localização e não significa online/offline.

V10 acrescenta `presenca_operacional` (texto não nulo, default NAO_INFORMADA) e `presenca_alterada_em` (TIMESTAMPTZ) em participante_compra. Constraints limitam os estados e exigem timestamp nulo somente para NAO_INFORMADA. Não há autor separado: somente o próprio participante declara presença.

- NAO_INFORMADA: sem declaração, timestamp nulo. Não significa remoto/ausente.
- PRESENTE: declaração ativa, timestamp preenchido.
- NAO_PRESENTE: declaração de ausência, timestamp preenchido.

O timestamp registra a última mudança efetiva, em UTC com precisão de microssegundos. Não existe histórico de períodos. Repetir o estado vigente não muda o timestamp. Não é permitido voltar para NAO_INFORMADA.

## Início e compatibilidade

O primeiro início efetivo marca apenas o iniciador como PRESENTE, na mesma transação dos snapshots. Demais participantes ficam NAO_INFORMADA. O POST de início continua sem body, retornando 201 na criação e 200 em replay autorizado.

Replay nunca inicializa nem reescreve presença. Dois inícios concorrentes marcam somente o executor da criação efetiva. Alterações posteriores do iniciador permanecem preservadas.

Registros anteriores à V10 ficam NAO_INFORMADA com timestamp nulo, inclusive finalizados. Não se deduz presença por autoria ou iniciador. Em Compra antiga em andamento, cada participante precisa declarar presença antes de colocar/restaurar ou finalizar. Inclusão de pendente e remoção mantêm a autorização anterior.

Reutilização não copia presença. Uma nova Compra inicializa seu próprio iniciador.

## Alteração da própria presença

`PUT /api/familias/{familiaId}/listas/{listaId}/compra/minha-presenca`

Bearer JWT obrigatório. Payload exclusivo:

```json
{"estado":"NAO_PRESENTE"}
```

Na V11, aceita somente NAO_PRESENTE como saída própria. PRESENTE recebe 409 e deve
usar `POST /minha-presenca/solicitacoes`. Estado nulo, omitido, desconhecido ou
NAO_INFORMADA recebe 400. Campos adicionais são rejeitados, inclusive usuário,
participante, executor e timestamp. A identidade vem exclusivamente do JWT.

200 retorna CompraResponse completa, com itens, participantes, auditorias e capabilities recalculadas, usando o mesmo mapper do GET. A consulta após o comando pode refletir outra alteração concorrente já efetivada; não é um recibo imutável da declaração enviada.

| Situação | HTTP |
| --- | --- |
| JWT ausente/inválido | 401 |
| Vínculo inativo ou membro não participante, inclusive administrador | 403 |
| Lista/Compra ausente ou incompatível com contexto | 404 |
| Compra fora de EM_ANDAMENTO, inclusive repetição depois de finalizar | 409 |
| Payload inválido | 400 |
| Repetição do estado vigente durante andamento | 200, preservando timestamp |

Erros mantêm o envelope timestamp/status/erro/mensagem/path/campos. O filtro de autenticação pode responder 401 sem JSON.

Não há toggle, versão ou chave de idempotência. Declarações opostas são executadas na ordem de aquisição do lock, não na ordem de envio dos dispositivos. Repetição após uma declaração oposta é uma nova definição de estado. Em falha de conexão, consultar GET antes de tentar novamente; não fazer retry automático da escrita.

## Resposta e capabilities

Cada participante inclui:

```json
{
  "presencaOperacional": {
    "estado": "NAO_INFORMADA",
    "alteradaEm": null
  }
}
```

Novas capabilities:

- contextoUsuario.podeAlterarPresenca/podeDeclararSaida: participante PRESENTE autorizado em Compra EM_ANDAMENTO.
- contextoUsuario.podeFinalizarCompra: participante ativo, PRESENTE, com as demais pré-condições já existentes. Ser responsável operacional não é requisito.
- contextoUsuario.precisaEstarPresenteParaFinalizar: explica somente o bloqueio por ausência; o frontend não deve deduzir essa causa localmente.
- item.acoes.podeColocarNoCarrinho: participante autorizado, PRESENTE, Compra EM_ANDAMENTO e item PENDENTE.

item.acoes.podeRestaurarNoCarrinho incorpora PRESENTE às condições anteriores. As capabilities representam ações disponíveis agora; não são autorização persistente nem promessa de sucesso de um comando concorrente.

participanteCompra continua significando participação. O frontend deve consumir podeColocarNoCarrinho explicitamente, sem deduzir presença por participação. Não há consulta extra por item: o mapper usa os participantes já carregados.

## Colocação e restauração

Exigem PRESENTE, inclusive em replays. NAO_INFORMADA/NAO_PRESENTE recebem 409 CONFLITO_DE_ESTADO, orientando declarar presença. A exigência é validada na aplicação, sob lock da Compra, antes da operação de domínio.

Replays autorizados preservam autoria/timestamps originais. Depois de declarar saída, repetir colocação/restauração recebe 409 sem desfazer o efeito anterior. Restauração mantém restauradoPor/Em, última colocação, transferência de responsabilidade e coerência com a remoção aprovada.

## Regras preservadas

- Adicionar item PENDENTE independe de presença.
- Solicitar/aprovar/rejeitar remoção, autoaprovação, replay e responsável permanecem inalterados.
- Declaração de saída não muda auditorias de itens; quando sair o responsável,
  aplica a sucessão operacional da V11.
- Finalização exige que o executor ativo seja participante da Compra e esteja PRESENTE; revisão continua disponível quando essa capability for falsa. A responsabilidade operacional não concede poder adicional de finalização.
- Ao finalizar, as últimas declarações ficam congeladas; não se marca ausência automaticamente.

Limitação aceita: responsável remoto pode solicitar e autoaprovar remoção. Isso não comprova retirada física e não será resolvido neste marco.

O GET da Compra finalizada preserva a última declaração. Ela não demonstra presença durante toda a Compra. Não é necessário acrescentar exibição de presença no resumo frontend neste MVP.

## Concorrência e frontend futuro

Mudança de presença usa o mesmo lock pessimista da Compra empregado nas mutações de itens. O participante é carregado depois desse lock. Finalização mantém a ordem ListaCompra → Compra; presença não adquire lock de lista depois de Compra.

Saída concorrente com colocação/restauração: a ação é autorizada antes da saída ou recusada depois dela. Presença concorrente com finalização: alteração ocorre antes do encerramento ou recebe conflito. Mesma declaração repetida não reescreve timestamp.

Todo GET completo de reconciliação deve ser aplicado integralmente no frontend. Aplicar somente um item descarta mudanças em presença e permissões dos demais itens. Resposta de alteração de presença também substitui a Compra completa. Respostas antigas devem ser descartadas após troca de contexto ou mutação posterior.

Polling futuro (FE #16) poderá consultar o GET; nunca altera presença nem determina online/offline. Não está implementado neste marco.

Frontend antigo contra backend novo não oferece declaração para outros participantes ou Compras antigas; pode receber 409 em carrinho/restauração. Frontend novo não deve liberar ações por fallback à participação se faltarem capabilities. Publicação exige coordenação dos dois lados e recarga de abas antigas. Não manter backend antigo executando mutações em paralelo: ele não valida presença.

## Testes

PresencaOperacionalTest: estados, timestamps, repetição, saída/retorno, congelamento e snapshots.

PresencaOperacionalIntegrationTests: HTTP/JWT, campos adicionais, capabilities/GET, autorização, replays, operações remotas preservadas, reutilização, migration V9 → V10 em schema isolado, constraints e concorrência com PostgreSQL real.

As fixtures de regressão declaram presença explicitamente quando colocação/restauração por outro participante faz parte do cenário. Não foram removidos cenários anteriores.
