# Edição dos dados básicos da ListaCompra

BE #1 → FE #2. Decisão aprovada: criador da lista ou administrador ativo da mesma família pode editar somente em EM_PREPARACAO. Participação, isoladamente, não concede permissão.

## HTTP

PUT /api/familias/{familiaId}/listas/{listaId}

Authorization: Bearer JWT. JSON de substituição dos dados básicos:

    {"nome":"Compras da semana","categoria":"SUPERMERCADO","estabelecimento":null}

Validações idênticas à criação: nome obrigatório não branco, até 120 caracteres; categoria obrigatória entre SUPERMERCADO, ROUPAS, BRINQUEDOS, ACESSORIOS, UTENSILIOS e OUTROS; estabelecimento opcional até 120 caracteres. Null ou ausência limpa estabelecimento. Não altera itens, participantes, criador ou criadaEm. atualizadaEm recebe o instante do backend.

Sucesso: 200 com ListaCompraDetalheResponse completo, no mesmo formato do GET, incluindo contextoUsuario.podeEditarDadosBasicos. A capability também integra GET e é false fora da preparação e para membros sem autorização. Vínculo inativo é recusado pelo backend.

- 400: campos inválidos, categoria desconhecida ou JSON inválido.
- 401: JWT ausente/inválido.
- 403: membro não autorizado, inativo ou externo à família.
- 404: lista inexistente ou incompatível com familiaId.
- 409: lista fora de EM_PREPARACAO.

Mantém o envelope de erros existente. O executor vem exclusivamente do JWT. Nenhuma migration, novo estado ou campo de auditoria é necessário.

## Concorrência e frontend

A edição usa ListaCompra PESSIMISTIC_WRITE, como o início da Compra. Se a edição obtiver o lock primeiro, o início observa os novos dados; se o início vencer, a edição encontra EM_COMPRA e retorna 409 sem alterar os dados. Duas edições válidas são serializadas; a última gravação prevalece, sem controle otimista de versão.

O response é consultado após o caso de uso, seguindo o padrão dos endpoints existentes; pode refletir uma transição concorrente já concluída. O frontend substitui o detalhe pela representação completa.

A UI usa exclusivamente podeEditarDadosBasicos para oferecer edição. Não infere permissão por criador, papel ou participação. O formulário mantém valores após falha, exibe erros localmente e consulta GET em 403/409. Falha dessa reconciliação bloqueia novo envio até atualização bem-sucedida.

## Validação

EditarDadosBasicosListaIntegrationTests: 10 testes HTTP/integração com JWT, PostgreSQL 18-alpine, Flyway e open-in-view=false, sem transação externa no teste HTTP. Cobertura de criador com papel MEMBRO, administrador não participante, participante/observador, inativo, 401/403/404/409, validações, GET, identidade e auditoria de criação preservadas, estados somente leitura e concorrência em ambas as ordens com espera de lock observada no PostgreSQL.
