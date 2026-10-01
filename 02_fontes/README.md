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
