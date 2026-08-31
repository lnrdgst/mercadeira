# Mercadeira

Backend do Mercadeira, uma aplicacao colaborativa para organizacao de compras entre membros de uma familia. Atualmente cobre usuarios, autenticacao, familia, solicitacoes de entrada e regras de preparacao de listas de compra.

## Stack

- Java 21
- Spring Boot, Spring Data JPA e Spring Security
- JWT
- PostgreSQL 18
- Flyway
- Maven
- Testcontainers com PostgreSQL 18-alpine

## Estrutura

```text
02_fontes/  Codigo-fonte da aplicacao Maven
docs/       Documentacao e artefatos de arquitetura
```

O codigo e organizado por dominio/feature, sem um pacote global generico de controllers, services ou repositories. Os modulos atuais incluem `usuario`, `autenticacao`, `familia`, `lista` e `compra`.

## Banco e migrations

O Flyway e o unico responsavel pela evolucao do schema. O Hibernate opera com `ddl-auto=validate`; nao cria nem altera tabelas.

Migrations existentes:

- `V1__estrutura_inicial.sql`
- `V2__complementa_fluxos_iniciais.sql`
- `V3__adiciona_hash_senha_usuario.sql`
- `V4__adiciona_cancelamento_solicitacao_familia.sql`

Migrations antigas sao imutaveis. Qualquer alteracao estrutural deve entrar em uma nova migration. O modelo completo atual esta em [docs/modelo-v4](docs/modelo-v4/); as versoes anteriores sao historico arquitetural.

## Configuracao local

As propriedades podem ser fornecidas por variaveis de ambiente ou por um arquivo `.env` dentro de `02_fontes/`, que nao deve ser versionado.

```env
DB_PASSWORD=...
JWT_SECRET=...
JWT_DURATION=PT1H
```

| Variavel | Uso | Default |
| --- | --- | --- |
| `DB_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/mercadeira` |
| `DB_USER` | Usuario do banco | `mercadeira_app` |
| `DB_PASSWORD` | Senha do banco | obrigatoria |
| `JWT_SECRET` | Chave para assinar JWT | obrigatoria |
| `JWT_DURATION` | Duracao do token | `PT1H` |

## Autenticacao

- Login por email e senha; a senha e persistida somente como hash.
- A API usa JWT Bearer e e stateless, sem HTTP Basic ou form login.
- O token contem somente o UUID do usuario no claim `sub` e expira, por padrao, em uma hora.
- Familia, papel e permissoes nao fazem parte do token: as autorizacoes familiares sao sempre validadas no banco.

## Regras de familia

- Um usuario possui no maximo um `MembroFamilia` com status `ATIVO`.
- Papeis: `ADMINISTRADOR` e `MEMBRO`. Familias: `ATIVA` e `INATIVA`.
- Ao criar familia, o criador passa a ser `ADMINISTRADOR` ativo e recebe um `codigoIngresso`.
- Um usuario sem familia ativa pode solicitar entrada em mais de uma familia. A solicitacao nasce `PENDENTE`.
- Um administrador pode `APROVAR` ou `REJEITAR`. A aprovacao cria um vinculo `MEMBRO` ativo.
- Aprovacao ou criacao da propria familia cancelam automaticamente as demais solicitacoes pendentes do usuario, usando `CANCELADA` e nao `REJEITADA`.
- `PENDENTE` e `CANCELADA` nao possuem responsavel nem data de resolucao; `APROVADA` e `REJEITADA` possuem ambos.

## Concorrencia

Operacoes sensiveis de solicitacao, aprovacao e criacao de familia serializam por usuario com lock pessimista. Depois do lock, o vinculo ativo e revalidado. O indice unico parcial de membro ativo permanece como garantia final do banco, e nao como fluxo normal de controle da aplicacao.

## Endpoints disponiveis

Rotas protegidas exigem `Authorization: Bearer <token>`. A identidade do executor vem do JWT; o frontend nao envia `executorId` nem papel.

| Metodo | Endpoint | Descricao |
| --- | --- | --- |
| `POST` | `/api/usuarios` | Cadastra usuario. |
| `POST` | `/api/autenticacao/login` | Autentica por email e senha e retorna JWT. |
| `GET` | `/api/familias/ativa` | Consulta a familia ativa do usuario. |
| `POST` | `/api/familias` | Cria familia para o usuario autenticado. |
| `POST` | `/api/familias/solicitacoes` | Solicita entrada por codigo da familia. |
| `GET` | `/api/familias/solicitacoes` | Lista solicitacoes pendentes para o administrador. |
| `GET` | `/api/familias/solicitacoes/minhas-pendentes` | Lista pendencias do usuario para onboarding. |
| `POST` | `/api/familias/solicitacoes/{id}/aprovar` | Aprova solicitacao como administrador. |
| `POST` | `/api/familias/solicitacoes/{id}/rejeitar` | Rejeita solicitacao como administrador. |

Os dois primeiros endpoints sao publicos; os demais sao protegidos.

## Onboarding familiar

```text
Login
  -> GET /api/familias/ativa
     -> 200: usuario possui familia ativa
     -> 204: GET /api/familias/solicitacoes/minhas-pendentes
             -> 200: usuario aguarda aprovacao em uma ou mais familias
             -> 204: usuario pode criar familia ou solicitar entrada
```

Este fluxo descreve o estado de dominio/API, nao a navegacao visual do frontend.

## Lista de compra

A camada de aplicacao de preparacao de listas ja suporta listas em `EM_PREPARACAO`: o criador torna-se participante, participantes devem pertencer a familia e podem ser removidos logicamente. Participantes ativos podem adicionar, editar, remover logicamente e reordenar itens, com categoria, unidade de medida e `ordem_exibicao`.

As operacoes de preparacao sao restritas a participantes ativos e ao status `EM_PREPARACAO`. A API REST de listas ainda nao esta disponivel.

## Testes

```powershell
cd 02_fontes
.\mvnw.cmd clean test
```

Os testes usam PostgreSQL 18-alpine via Testcontainers, aplicam Flyway, validam o schema com Hibernate e executam testes de integracao. Em Unix, use `./mvnw clean test`.

## Execucao local

Com PostgreSQL local e as variaveis configuradas:

```powershell
cd 02_fontes
.\mvnw.cmd spring-boot:run
```

Em Unix: `./mvnw spring-boot:run`.

## Estado atual

Implementado: usuarios, autenticacao JWT, familia, onboarding familiar e regras de preparacao de ListaCompra.

Ainda pendente: REST de listas, inicio de compra, compra ativa, snapshots, historico e atualizacao em tempo real por WebSocket.

## Cuidados

- Nao versione `.env` nem coloque `DB_PASSWORD` ou `JWT_SECRET` no codigo.
- Nao altere migrations antigas nem use `ddl-auto=update`.
- Nao inclua familia ou papel no JWT.
- Nao confie em identificadores de executor enviados pelo cliente.
