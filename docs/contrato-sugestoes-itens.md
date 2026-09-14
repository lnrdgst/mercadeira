# Sugestões de itens por família

GET /api/familias/{familiaId}/itens/sugestoes?termo=arr

Bearer JWT obrigatório; qualquer membro ATIVO da família pode consultar. Não exige autoria ou participação em uma lista. Vínculo ausente/inativo retorna 403 pelo envelope existente; token ausente/inválido retorna 401. Termo com mais de 200 caracteres retorna 400. Omitido ou vazio retorna recentes.

200 retorna array, inclusive vazio:

    [{"descricao":"Arroz","unidadeMedida":"KG"}]

## Origem e regra

Sem cadastro mestre, tabelas ou migrations novas. Consulta SQL parametrizada no backend:

- ItemLista não removido de listas EM_PREPARACAO.
- ItemCompra não REMOVIDO de compras EM_ANDAMENTO/FINALIZADA, vinculadas a listas EM_COMPRA/FINALIZADA da família.
- Inclui snapshots de origem e itens adicionados durante a Compra. Não duplica ItemLista e seu snapshot, pois as fontes usam estados distintos da lista.
- PENDENTE e REMOCAO_SOLICITADA ainda são vocabulário utilizado pela família; só REMOVIDO é excluído.
- Canceladas ficam fora. Não representa recomendação de compra nem catálogo completo de todos os ciclos.

Descrição e termo têm espaços consecutivos reduzidos e extremos removidos. Busca por trecho sem distinguir caixa; acentos continuam significativos. % e _ são texto literal. Distinção usa descrição normalizada em minúsculas, independentemente da unidade. A ocorrência mais recente fornece descrição e unidade (inclusive null).

Recência: atualizadoEm do ItemLista; adicionadoEm do ItemCompra incluído durante a compra, ou iniciadaEm para snapshot. Desempate entre ocorrências por UUID; resultados por recência, descrição normalizada e UUID. Limite fixo de 10 no servidor. Sem paginação/frequência/algoritmo de recomendação.

A consulta usa relacionamentos família → lista → item e lista → compra → item; índices existentes de itens por lista/compra são preservados. Nenhum índice especulativo ou alteração das migrations V1–V9. A busca textual faz varredura do conjunto da família; reavaliar planos de consulta somente se volume real justificar.

## Frontend

Autocomplete apenas na preparação, em inclusão e edição. Foco vazio busca recentes; digitação aguarda 200 ms. Resultados antigos são ignorados ao mudar termo/fonte ou sair do campo. Teclado: setas percorrem, Enter seleciona opção ativa, Escape fecha sugestões sem fechar o diálogo e Tab segue o formulário. Texto livre e envio sem seleção continuam válidos.

Selecionar altera somente descrição e unidade, ambos editáveis. Quantidade, marca e observações já preenchidas permanecem intactas. Loading, vazio e falha de sugestões não bloqueiam formulário. 401 preserva encerramento da sessão existente.

## Verificação

SugestoesItensIntegrationTests cobre autorização familiar, snapshots finalizados e inclusão durante compra, removidos/canceladas, deduplicação, recência, limite, termo literal e validação. Regressões relacionadas: ListaCompraApplicationTests e EditarDadosBasicosListaIntegrationTests.
