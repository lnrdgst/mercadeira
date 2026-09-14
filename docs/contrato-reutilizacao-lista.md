# Reutilização de compra finalizada

BE #12 → FE #14. Sem migration ou alteração da Compra original.

## Consulta e autorização

`GET /api/familias/{familiaId}/listas/{listaId}/compra` acrescenta `contextoUsuario.podeReutilizarLista` (boolean). A consulta exige vínculo familiar ativo. A capability é verdadeira com família ATIVA, ListaCompra e Compra FINALIZADA e nenhuma REMOCAO_SOLICITADA. Ser participante da compra original não é necessário: um membro ativo pode criar sua própria preparação. O frontend consome somente a capability; o POST revalida as condições.

## Criação

`POST /api/familias/{familiaId}/listas/{listaId}/reutilizar`

Bearer JWT obrigatório, sem body. Retorna `201 Created`, `ListaCompraResponse` (mesmo formato da criação de lista) e `Location: /api/familias/{familiaId}/listas/{novoId}`. A nova lista pertence à mesma família, tem estado EM_PREPARACAO e copia nome, categoria e estabelecimento dos snapshots da Compra. Executor é criador e único participante inicial.

Fonte exclusiva: ItemCompra da Compra finalizada. Copia NO_CARRINHO e PENDENTE, inclusive os adicionados durante a compra; ignora REMOVIDO. Para cada item cria NOVO ItemLista com descrição, quantidade, unidade, marca e observações. Preserva ordem relativa e atribui posições consecutivas. IDs e auditoria de preparação são novos, atribuídos ao executor no momento da criação.

Não transfere status operacional, solicitadoPor/decididaPor, remoção/restauração, colocadoNoCarrinhoPor/Em, adicionadoPor/Em da Compra, capabilities ou vínculo com ItemCompra anterior. Não cria Compra nova. A lista e seus itens podem ser editados/removidos pelas regras já existentes de preparação. Se todos os itens forem removidos, cria preparação vazia; iniciar continuará exigindo item ativo.

## Consistência e erros

Transação única com lock da lista de origem; falha de cópia reverte lista, participante e itens. Origem permanece intacta. Respostas seguem envelope de erro existente: 401 sem JWT válido, 403 sem membro ativo, 404 lista inexistente/outra família, 409 origem não finalizada/inconsistente ou família inativa.

Operação não idempotente: cada chamada válida cria uma nova lista independente. Frontend bloqueia duplo envio e não repete automaticamente após erro de rede; deve orientar conferir Minhas Listas antes de repetir. Em 403/409, GET reconcilia o resumo/capability; não repetir POST enquanto a reconciliação falhar.

## Evidência e teste manual

ReutilizarListaIntegrationTests: seis cenários HTTP/PostgreSQL cobrem campos e estados, itens adicionados durante a compra, origem intacta, membro observador, participantes novos, erros/autorização, preparação vazia, chamadas distintas e rollback por falha real de persistência. Mais 28 regressões de finalização aprovadas.

Manual integrado: em Minhas Listas abra resumo com carrinho, pendente, removido e item acrescentado durante compra; cancele e depois confirme reutilização. Confira nova preparação, metadados e itens, edite/remova um item novo e confira origem intacta após F5. Repita com observador ativo e com todos removidos. Verifique foco, Escape, Tab e viewport móvel.
