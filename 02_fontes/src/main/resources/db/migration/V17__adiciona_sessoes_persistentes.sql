CREATE TABLE sessao_persistente (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    ultimo_uso_em TIMESTAMP WITH TIME ZONE NOT NULL,
    revogado_em TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sessao_persistente_usuario_ativa
    ON sessao_persistente(usuario_id) WHERE revogado_em IS NULL;
