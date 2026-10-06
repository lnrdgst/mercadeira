CREATE TABLE token_redefinicao_senha (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_em TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_token_redefinicao_senha_usuario_ativo ON token_redefinicao_senha(usuario_id) WHERE usado_em IS NULL;
