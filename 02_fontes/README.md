# Mercadeira API

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
