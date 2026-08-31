ALTER TABLE solicitacao_entrada_familia
    DROP CONSTRAINT ck_solicitacao_entrada_familia_status,
    DROP CONSTRAINT ck_solicitacao_entrada_familia_status_resolucao;

ALTER TABLE solicitacao_entrada_familia
    ADD CONSTRAINT ck_solicitacao_entrada_familia_status
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
    ADD CONSTRAINT ck_solicitacao_entrada_familia_status_resolucao
        CHECK (
            (status IN ('PENDENTE', 'CANCELADA') AND resolvida_por_membro_familia_id IS NULL)
            OR (status IN ('APROVADA', 'REJEITADA') AND resolvida_por_membro_familia_id IS NOT NULL)
        );
