CREATE TABLE solicitacao_responsabilidade_operacional (
    id UUID PRIMARY KEY,
    compra_id UUID NOT NULL REFERENCES compra (id),
    solicitante_id UUID NOT NULL,
    responsavel_atual_id UUID NOT NULL,
    ciclo_operacional BIGINT NOT NULL,
    responsabilidade_revisao BIGINT NOT NULL,
    solicitada_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL,
    encerrada_por_id UUID,
    encerrada_em TIMESTAMPTZ,
    CONSTRAINT fk_solicitacao_responsabilidade_solicitante_mesma_compra
        FOREIGN KEY (compra_id, solicitante_id) REFERENCES participante_compra (compra_id, id),
    CONSTRAINT fk_solicitacao_responsabilidade_responsavel_mesma_compra
        FOREIGN KEY (compra_id, responsavel_atual_id) REFERENCES participante_compra (compra_id, id),
    CONSTRAINT fk_solicitacao_responsabilidade_encerrada_por_mesma_compra
        FOREIGN KEY (compra_id, encerrada_por_id) REFERENCES participante_compra (compra_id, id),
    CONSTRAINT ck_solicitacao_responsabilidade_estado
        CHECK (estado IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
    CONSTRAINT ck_solicitacao_responsabilidade_encerramento
        CHECK ((estado = 'PENDENTE' AND encerrada_por_id IS NULL AND encerrada_em IS NULL)
            OR (estado IN ('APROVADA', 'REJEITADA', 'CANCELADA') AND encerrada_em IS NOT NULL))
);

CREATE UNIQUE INDEX uk_solicitacao_responsabilidade_pendente_por_solicitante
    ON solicitacao_responsabilidade_operacional (compra_id, solicitante_id)
    WHERE estado = 'PENDENTE';
CREATE INDEX ix_solicitacao_responsabilidade_pendente_por_compra
    ON solicitacao_responsabilidade_operacional (compra_id, solicitada_em, id)
    WHERE estado = 'PENDENTE';
