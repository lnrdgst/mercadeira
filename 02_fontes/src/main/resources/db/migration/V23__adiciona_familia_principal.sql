ALTER TABLE membro_familia
    ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uk_membro_familia_usuario_principal
    ON membro_familia (usuario_id)
    WHERE principal = TRUE;

ALTER TABLE membro_familia
    ADD CONSTRAINT ck_membro_familia_principal_ativo
    CHECK (NOT principal OR status = 'ATIVO');
