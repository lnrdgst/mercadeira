# Mercadeira API

## Registros financeiros da compra

Uma Compra pode ter zero ou mais registros financeiros. O valor é opcional: finalizar sem registros é permitido. Os registros são vinculados à Compra, e `totalRegistrado` é sempre calculado pela soma dos registros persistidos, sem coluna de total redundante.

Durante `EM_ANDAMENTO`, somente participante ativo da Compra com presença operacional informada pode criar ou remover registros. Administrador(a) familiar que não participa da Compra não recebe essa capacidade. Em `FINALIZADA`, a Compra permanece operacionalmente imutável, mas participante autorizado pode incluir ou remover registros financeiros sem presença operacional atual. Isso não reabre a Compra, não altera seu status nem seus snapshots operacionais; correções de valor ou estabelecimento são feitas removendo o registro e incluindo-o novamente.

- `POST /api/familias/{familiaId}/listas/{listaId}/compra/registros-financeiros` recebe `{ "valor": 82.40, "estabelecimentoNome": "Mercado Central" }` e retorna `201 Created` com `CompraResponse` atualizado.
- `DELETE /api/familias/{familiaId}/listas/{listaId}/compra/registros-financeiros/{registroId}` retorna `200 OK` com `CompraResponse` atualizado.

Cada registro em `CompraResponse.registrosFinanceiros` tem `id`, `valor`, `tipo`, `estabelecimentoNome` e `criadoEm`; registros NFC-e também podem trazer os metadados fiscais opcionais. O total vem em `CompraResponse.totalRegistrado`.

### NFC-e por QR Code

O QR Code é uma forma de captura do mesmo `RegistroFinanceiroCompra`, nunca um domínio financeiro paralelo. O frontend envia o texto lido para `POST /api/familias/{familiaId}/listas/{listaId}/compra/registros-financeiros/analisar-qr`; esse endpoint apenas interpreta dados já presentes no conteúdo e não persiste nada. Depois da confirmação do usuário, `POST /api/familias/{familiaId}/listas/{listaId}/compra/registros-financeiros/nfce` recebe valor, estabelecimento e os metadados fiscais disponíveis.

O tipo de registro pode ser `MANUAL` ou `NFCE`. Para NFC-e, `chaveNfce`, `urlConsulta`, `cnpjEmitente` e `dataHoraDocumento` são opcionais. O parser tolera QR parcial: valor e estabelecimento podem não estar disponíveis, mas o valor continua obrigatório para persistir. A mesma chave de 44 dígitos não pode ser repetida na mesma Compra; pode existir em Compras diferentes.

Por segurança, a API **não faz HTTP, scraping ou consulta SEFAZ** para URLs lidas do QR. Ela somente interpreta dados já presentes e armazena a URL como metadado opcional. Consulta fiscal externa e itens do cupom continuam fora de escopo.

O scanner do frontend usa `getUserMedia` com preferência pela câmera traseira e `@zxing/browser`, inclusive em Safari/iPhone. O modal via Portal usa preview `playsInline` e encerra a câmera ao detectar, cancelar ou fechar. Permissão negada, câmera indisponível ou contexto não seguro mantêm disponíveis a colagem do conteúdo e o registro manual. Câmera requer HTTPS em DSV/PWA; `localhost` é considerado contexto seguro, mas HTTP por IP de rede pode ser bloqueado.

## Minha conta

Todos os endpoints abaixo são autenticados e operam exclusivamente sobre o usuário identificado pelo UUID no `sub` do JWT. Não é aceito um identificador de usuário arbitrário.

### Consultar conta

`GET /api/usuarios/me` retorna os dados públicos da conta autenticada:

```json
{ "id": "...", "nome": "...", "email": "..." }
```

### Atualizar dados pessoais

`PATCH /api/usuarios/me` permite alterar nome e e-mail.

- Alterar somente o nome não exige a senha atual.
- Alterar o e-mail exige `senhaAtual`.
- O e-mail é normalizado com `trim` e lowercase; sua unicidade é case-insensitive.
- A operação é atômica: senha atual inválida não altera nome nem e-mail.

```json
{ "nome": "Nome atualizado", "email": "novo@email.com", "senhaAtual": "..." }
```

### Alterar senha

`PUT /api/usuarios/me/senha` altera a senha da conta autenticada.

```json
{ "senhaAtual": "...", "novaSenha": "..." }
```

A senha atual é obrigatória. A API reutiliza a política de senha existente e o `PasswordEncoder` configurado. Hashes de senha nunca são retornados.

### Sessão

Como o JWT usa o UUID no `sub`, mudar nome ou e-mail não exige logout automático. A sessão atual permanece válida.

## Recuperação de senha

`POST /api/autenticacao/esqueci-senha` é público e recebe `{ "email": "usuario@exemplo.com" }`. A resposta é sempre genérica, inclusive para e-mails inexistentes, evitando enumeração de contas. O e-mail é normalizado (trim/lowercase) e há cooldown simples de 60 segundos por conta, sem alterar a resposta pública.

`POST /api/autenticacao/redefinir-senha` é público e recebe `{ "token": "...", "novaSenha": "..." }`. Links expiram em 30 minutos por padrão, são de uso único e nova solicitação invalida links anteriores. O banco armazena somente SHA-256 do token, nunca o valor puro.

O envio usa a abstração `EmailService` com SMTP configurável. No Railway, configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_ENABLED`, `APP_FRONTEND_URL` e `PASSWORD_RESET_EXPIRATION_MINUTES` (opcionalmente `PASSWORD_RESET_COOLDOWN_SECONDS`). O link enviado é `${APP_FRONTEND_URL}/redefinir-senha?token=...`; não inclua secrets no repositório.

Não há revogação de JWT no projeto: JWTs emitidos antes da redefinição continuam válidos até sua expiração.
## Sessão persistente

O access token JWT Bearer continua com duração curta, configurada por `JWT_DURATION` (padrão `PT1H`). O login também cria uma sessão persistente independente, com duração máxima absoluta configurada por `SESSION_EXPIRATION_DAYS` (padrão `180`). A renovação do access token não estende essa data.

O banco guarda em `sessao_persistente` somente o SHA-256 do refresh token, nunca o valor puro. Cada login cria uma sessão por dispositivo. Em `POST /api/autenticacao/refresh`, o refresh atual é localizado, validado e imediatamente rotacionado; o token anterior deixa de funcionar. O endpoint recebe `{ "refreshToken": "..." }` e devolve o novo access token, expiração e refresh token. `POST /api/autenticacao/logout` recebe o refresh atual e revoga somente aquela sessão.

O frontend DSV e a API Railway estão em sites diferentes (`vercel.app` e `up.railway.app`). Por isso não é usado cookie `HttpOnly` cross-site: `SameSite=None; Secure` depende de cookies de terceiros, que Safari/iOS pode bloquear. O refresh fica no mesmo armazenamento local já usado pelo access token; nunca entra em URL, logs ou respostas de erro. Quando os domínios passarem a compartilhar um site registrável, a estratégia pode migrar para cookie `HttpOnly`.

O frontend renova silenciosamente perto da expiração ou após um `401`, com uma única renovação compartilhada para requisições concorrentes. Falhas de rede e respostas `5xx` preservam a sessão local e não provocam logout. Refresh inválido, expirado ou revogado limpa a sessão e leva ao login. Não há CORS com credentials nesta estratégia; as origens continuam explícitas em `CORS_ALLOWED_ORIGINS`.

Alterar a senha em Minha Conta ou redefini-la revoga todas as sessões persistentes do usuário. Na alteração autenticada, o frontend encerra a sessão local para exigir novo login. Access tokens já emitidos expiram no máximo em uma hora.
