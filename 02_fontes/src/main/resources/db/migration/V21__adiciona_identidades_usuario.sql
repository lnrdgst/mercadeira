ALTER TABLE usuario
    ALTER COLUMN senha_hash DROP NOT NULL;

CREATE TABLE usuario_identidade (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    provedor VARCHAR(20) NOT NULL,
    provedor_subject VARCHAR(255),
    email_provedor VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_usuario_identidade_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_usuario_identidade_usuario_provedor UNIQUE (usuario_id, provedor),
    CONSTRAINT uk_usuario_identidade_provedor_subject UNIQUE (provedor, provedor_subject),
    CONSTRAINT ck_usuario_identidade_provedor_subject CHECK (
        (provedor = 'LOCAL' AND provedor_subject IS NULL)
        OR (provedor = 'GOOGLE' AND provedor_subject IS NOT NULL)
    )
);

INSERT INTO usuario_identidade (id, usuario_id, provedor, provedor_subject, email_provedor, criado_em, atualizado_em)
SELECT md5(id::text || ':LOCAL')::uuid, id, 'LOCAL', NULL, email, criado_em, atualizado_em
FROM usuario;
