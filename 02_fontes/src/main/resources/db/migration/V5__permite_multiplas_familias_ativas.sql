DROP INDEX uk_membro_familia_usuario_ativo;

CREATE INDEX ix_membro_familia_usuario_ativo
    ON membro_familia (usuario_id)
    WHERE status = 'ATIVO';
