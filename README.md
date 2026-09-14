# Mercadeira

Backend de compras colaborativas em família. Implementa usuários, autenticação JWT, família/onboarding, preparação de listas e Compra com snapshots, carrinho, remoção controlada, restauração e finalização.

## Stack e estrutura

- Java 21, Spring Boot 4.1.1, Spring Data JPA e Spring Security.
- PostgreSQL, Flyway, Maven Wrapper e JWT HS256.
- Testcontainers com PostgreSQL `18-alpine` para integração.

`02_fontes/` contém a aplicação Maven, organizada por domínio (`usuario`, `autenticacao`, `familia`, `lista`, `compra`). `docs/` contém contratos e referências arquiteturais.

## Configuração e execução local

Requisitos: JDK 21 e PostgreSQL acessível, com banco e usuário criados. O primeiro uso do Maven Wrapper requer acesso à rede para baixar Maven/dependências.

As propriedades vêm de variáveis de ambiente ou de um arquivo `.env` dentro de `02_fontes/`, não versionado e em formato de propriedades (`CHAVE=valor`).

| Variável | Uso | Default |
| --- | --- | --- |
| `DB_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/mercadeira` |
| `DB_USER` | Usuário do banco | `mercadeira_app` |
| `DB_PASSWORD` | Senha do banco | obrigatória |
| `JWT_SECRET` | Chave HS256 em Base64, com ao menos 32 bytes antes da codificação | obrigatória |
| `JWT_DURATION` | Duração do token | `PT1H` |

Com o banco e as variáveis configurados:

```powershell
cd 02_fontes
.\mvnw.cmd spring-boot:run
```

Em Unix: `./mvnw spring-boot:run`. Execute a partir de `02_fontes/` para carregar o `.env` local. A API usa a porta padrão 8080, salvo configuração externa (`SERVER_PORT`). Flyway aplica migrations e Hibernate valida o schema na inicialização.

## Banco e migrations

Flyway é o único responsável pela evolução do schema. Hibernate usa `ddl-auto=validate`, com `open-in-view=false`. Não alterar migrations antigas nem usar `ddl-auto=update`.

Migrations em [02_fontes/src/main/resources/db/migration](02_fontes/src/main/resources/db/migration/):

- `V1__estrutura_inicial.sql`
- `V2__complementa_fluxos_iniciais.sql`
- `V3__adiciona_hash_senha_usuario.sql`
- `V4__adiciona_cancelamento_solicitacao_familia.sql`
- `V5__permite_multiplas_familias_ativas.sql`
- `V6__permite_participante_direto_na_compra.sql`
- `V7__adiciona_autoria_item_compra.sql`
- `V8__adiciona_autoria_finalizacao_compra.sql`
- `V9__adiciona_restauracao_item_compra.sql`

[docs/modelo-v4](docs/modelo-v4/) é uma referência arquitetural; as migrations e os contratos atuais de Compra documentam a evolução posterior.

## Autenticação e família

Login por email/senha; somente o hash da senha é persistido. A API é stateless, sem HTTP Basic ou form login. Rotas protegidas exigem `Authorization: Bearer <token>`.

O JWT identifica o usuário pelo UUID no claim `sub`. Família, papel e permissões são validados no banco, não carregados no token. O cliente não envia executor, papel ou auditorias para determinar a autorização.

- Um usuário pode ter vínculos `MembroFamilia` ativos em múltiplas famílias.
- Papéis: `ADMINISTRADOR` e `MEMBRO`; famílias: `ATIVA` e `INATIVA`.
- Criar família torna o criador administrador ativo e gera `codigoIngresso`.
- Usuário sem família ativa pode solicitar entrada em mais de uma família; a solicitação nasce `PENDENTE`.
- Administrador pode aprovar ou rejeitar; aprovação cria vínculo de membro ativo. Criação de família e aprovação não alteram outras solicitações pendentes.
- `PENDENTE` e `CANCELADA` não possuem autor/data de resolução; `APROVADA` e `REJEITADA` possuem ambos.
- Decisões concorrentes usam lock pessimista na solicitação. Vínculos e papéis são validados na família do recurso; o índice parcial de membro ativo por usuário não é único.

## Endpoints de usuário e família

Somente cadastro e login são públicos.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/api/usuarios` | Cadastra usuário. |
| `POST` | `/api/autenticacao/login` | Retorna JWT. |
| `GET` | `/api/usuarios/me` | Consulta usuário autenticado. |
| `GET` | `/api/familias` | Lista famílias ativas do usuário, possivelmente vazia. |
| `POST` | `/api/familias` | Cria família. |
| `GET` | `/api/familias/{familiaId}/membros` | Consulta membros da família. |
| `POST` | `/api/familias/solicitacoes` | Solicita entrada por código. |
| `GET` | `/api/familias/solicitacoes/minhas-pendentes` | Consulta pendências do usuário: 200 com coleção ou 204 sem pendências. |
| `GET` | `/api/familias/{familiaId}/solicitacoes` | Consulta pendências para administrador. |
| `POST` | `/api/familias/{familiaId}/solicitacoes/{solicitacaoId}/aprovar` | Aprova entrada. |
| `POST` | `/api/familias/{familiaId}/solicitacoes/{solicitacaoId}/rejeitar` | Rejeita entrada. |

No onboarding, após login, consultar famílias e solicitações próprias pendentes para distinguir usuário sem família de usuário aguardando aprovação.

## Preparação de listas

Prefixo das rotas: `/api/familias/{familiaId}/listas`.

| Método | Sufixo | Descrição |
| --- | --- | --- |
| `GET` | (raiz) | Lista listas da família, incluindo finalizadas. |
| `POST` | (raiz) | Cria lista; retorna 201. |
| `GET` | `/{listaId}` | Consulta detalhes e contexto do usuário. |
| `GET` | `/{listaId}/participantes` | Consulta participantes ativos. |
| `POST` | `/{listaId}/participantes` | Adiciona participante; retorna 201. |
| `DELETE` | `/{listaId}/participantes/{membroFamiliaId}` | Remove participante logicamente; retorna 204. |
| `GET` | `/{listaId}/itens` | Consulta itens ativos, ordenados. |
| `POST` | `/{listaId}/itens` | Adiciona item; retorna 201. |
| `PUT` | `/{listaId}/itens/{itemId}` | Edita item. |
| `DELETE` | `/{listaId}/itens/{itemId}` | Remove item logicamente; retorna 204. |
| `PUT` | `/{listaId}/itens/ordem` | Reordena itens; retorna 204. |

O criador torna-se participante. As mutações respeitam `EM_PREPARACAO` e as permissões específicas de cada operação. O backend fornece contexto/capabilities e revalida autorização; o frontend não deve reconstruir essas regras.

Iniciar exige participante da lista com vínculo familiar ativo e pelo menos um item ativo (`removidoEm IS NULL`). Cria snapshots dos participantes e itens, muda a lista para `EM_COMPRA` e cria Compra `EM_ANDAMENTO`. ItemLista da preparação e ItemCompra do snapshot são recursos distintos.

Edição de nome, categoria e estabelecimento: PUT /api/familias/{familiaId}/listas/{listaId}, com 200 e detalhe completo. Somente criador ou administrador ativo da família, durante EM_PREPARACAO, conforme contextoUsuario.podeEditarDadosBasicos. A edição usa lock compartilhado com o início da Compra; após iniciar, recebe 409. [Contrato e validações](docs/contrato-edicao-lista.md).

Sugestões de itens por família: GET /api/familias/{familiaId}/itens/sugestoes?termo=arr. Membro ativo recebe até 10 descrições/unidades derivadas dos itens existentes, sem duplicatas por caixa/espaços; termo vazio retorna recentes. [Contrato](docs/contrato-sugestoes-itens.md).

## Compra

Prefixo das rotas: `/api/familias/{familiaId}/listas/{listaId}/compra`.

| Método | Sufixo | Descrição |
| --- | --- | --- |
| `POST` | (raiz) | Inicia sem body: 201 com Location; replay válido retorna 200. Ambos retornam Compra completa. |
| `GET` | (raiz) | Consulta Compra, inclusive finalizada, com snapshots, itens, auditorias e capabilities. |
| `POST` | `/itens` | Inclui item durante a Compra; body com dados do item, retorna 201 e ItemCompra completo. |
| `POST` | `/itens/{itemCompraId}/colocar-no-carrinho` | Coloca item no carrinho. |
| `POST` | `/itens/{itemCompraId}/solicitar-remocao` | Solicita remoção do carrinho. |
| `POST` | `/itens/{itemCompraId}/aprovar-remocao` | Aprova remoção. |
| `POST` | `/itens/{itemCompraId}/rejeitar-remocao` | Rejeita remoção. |
| `POST` | `/itens/{itemCompraId}/restaurar-no-carrinho` | Restaura item removido no carrinho. |
| `POST` | `/finalizar` | Finaliza Compra e lista; retorna 200 e Compra completa, inclusive em replay autorizado. |

As cinco ações sobre `itens/{itemCompraId}` não recebem body e retornam 200 com ItemCompra completo. Finalização também não recebe body. Autor e datas vêm do servidor.

### Remoção controlada e restauração

Estados dos itens: `PENDENTE`, `NO_CARRINHO`, `REMOCAO_SOLICITADA` e `REMOVIDO`. Removidos continuam em `itens[]`; não há exclusão física.

Participante autorizado pode solicitar remoção de item no carrinho. A decisão pertence ao responsável atual pela colocação: aprovação muda para `REMOVIDO`; rejeição retorna a `NO_CARRINHO`. Usar `acoes.podeSolicitarRemocao` e `acoes.podeDecidirRemocao`.

Restauração muda `REMOVIDO` para `NO_CARRINHO`, com remoção aprovada coerente, Compra em andamento e participante autorizado. Usar `acoes.podeRestaurarNoCarrinho`. O restaurador torna-se responsável atual pelo carrinho; auditorias da remoção e inclusão são preservadas. Replay autorizado do estado vigente retorna 200 sem substituir a autoria daquela restauração.

Contratos: [Compra 2E — remoção](docs/contratos-compra-2e.md) e [Compra 4 — restauração](docs/contratos-compra-4.md).

### Revisão e finalização

Revisão é uma etapa da interface, sem novo estado persistido. Usar `contextoUsuario.podeFinalizarCompra`. Qualquer participante da Compra com vínculo familiar ativo pode finalizar quando as condições de domínio permitem.

- `PENDENTE` não bloqueia: continua pendente, representando item não comprado.
- `NO_CARRINHO` e `REMOVIDO` são preservados, com suas auditorias.
- `REMOCAO_SOLICITADA` bloqueia com 409; a decisão precisa ser resolvida. Compra sem itens também é inválida.
- Compra e lista passam a `FINALIZADA` na mesma transação. `finalizadaPor` usa o snapshot do participante; `finalizadaEm` registra o instante do servidor.
- Replay autorizado retorna 200 com Compra completa e preserva autor/data originais.
- Após finalizar, mutações dos itens ficam bloqueadas; GET recupera o resumo, inclusive após F5.

O frontend deve substituir o recurso local pela resposta completa e reconciliar via GET em conflitos 409. Capabilities não substituem a validação concorrente no POST. Detalhes em [Compra 3 — finalização](docs/contratos-compra-3.md).

## Testes

Requisitos: JDK 21 e Docker em execução com suporte a containers Linux para Testcontainers. O primeiro uso precisa baixar dependências e a imagem `postgres:18-alpine`.

```powershell
cd 02_fontes
.\mvnw.cmd clean test
```

Em Unix: `./mvnw clean test`. A suíte inclui domínio, aplicação, HTTP com MockMvc/JWT e concorrência. Integrações usam PostgreSQL via Testcontainers, Flyway V1–V9 e validação de schema com Hibernate.

Para executar somente a cobertura HTTP de restauração, dentro de `02_fontes/`:

```powershell
.\mvnw.cmd "-Dtest=RestauracaoItemCompraHttpIntegrationTests" test
```

Relatórios: `02_fontes/target/surefire-reports/`. A evidência registrada no contrato Compra 4 é de 305 testes aprovados em 12/09/2026; não representa uma nova execução ao atualizar este README. Naquela execução houve demora no shutdown do Surefire, registrada no contrato.

## Limites atuais e V1

Ainda sem histórico dedicado/agregado, reabertura, cancelamento operacional ou atualização em tempo real por WebSocket.

Não há histórico completo de ciclos de remoção/restauração, versionamento de comandos ou idempotency-key. Replay de restauração considera o estado vigente; não permite reconstruir ciclos anteriores.

No piloto reduzido da V1, resumos das compras finalizadas acessíveis pelo frontend em Minhas Listas são suficientes. Histórico dedicado permanece no backlog; sua ausência não impede consultar Compra finalizada.

Não versionar `.env`, `DB_PASSWORD` ou `JWT_SECRET`; não incluir família/papel no JWT nem confiar em identificadores de executor enviados pelo cliente.
