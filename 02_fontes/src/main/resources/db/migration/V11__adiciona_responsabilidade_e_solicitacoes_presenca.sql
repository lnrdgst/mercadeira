ALTER TABLE compra
    ADD COLUMN responsavel_operacional_id UUID,
    ADD COLUMN ciclo_operacional BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ciclo_operacional_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN responsabilidade_revisao BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN responsabilidade_anterior_id UUID,
    ADD COLUMN responsabilidade_alterada_por_id UUID,
    ADD COLUMN responsabilidade_alterada_em TIMESTAMPTZ,
    ADD COLUMN responsabilidade_motivo VARCHAR(30),
    ADD CONSTRAINT fk_compra_responsavel_operacional_mesma_compra
        FOREIGN KEY (id, responsavel_operacional_id)
        REFERENCES participante_compra (compra_id, id),
    ADD CONSTRAINT fk_compra_responsabilidade_anterior_mesma_compra
        FOREIGN KEY (id, responsabilidade_anterior_id)
        REFERENCES participante_compra (compra_id, id),
    ADD CONSTRAINT fk_compra_responsabilidade_autor_mesma_compra
        FOREIGN KEY (id, responsabilidade_alterada_por_id)
        REFERENCES participante_compra (compra_id, id),
    ADD CONSTRAINT ck_compra_responsabilidade_motivo
        CHECK (responsabilidade_motivo IS NULL OR responsabilidade_motivo IN
            ('INICIO_COMPRA', 'PRIMEIRA_ENTRADA', 'SUCESSAO', 'REASSUNCAO',
             'SEM_PRESENTES', 'BOOTSTRAP_V10', 'LEGADO_SEM_ELEGIVEL'));

-- Dados V10 em andamento conservam presencas. Apenas um participante ja presente e
-- com vinculo ativo pode ser escolhido; compras finalizadas nao recebem historico novo.
WITH candidatos AS (
    SELECT compra.id AS compra_id, candidato.id AS responsavel_id,
           candidato.presenca_alterada_em AS presenca_alterada_em
    FROM compra
    JOIN LATERAL (
        SELECT participante.id, participante.presenca_alterada_em
        FROM participante_compra participante
        JOIN membro_familia membro ON membro.id = participante.membro_familia_id
        WHERE participante.compra_id = compra.id
          AND participante.presenca_operacional = 'PRESENTE'
          AND membro.status = 'ATIVO'
        ORDER BY CASE WHEN participante.membro_familia_id = compra.iniciada_por_membro_familia_id THEN 0 ELSE 1 END,
                 participante.presenca_alterada_em, participante.id
        LIMIT 1
    ) candidato ON TRUE
    WHERE compra.status = 'EM_ANDAMENTO'
)
UPDATE compra
SET responsavel_operacional_id = candidato.responsavel_id,
    ciclo_operacional = 1,
    ciclo_operacional_ativo = TRUE,
    responsabilidade_revisao = 1,
    responsabilidade_alterada_em = COALESCE(candidato.presenca_alterada_em, compra.iniciada_em),
    responsabilidade_motivo = 'BOOTSTRAP_V10'
FROM candidatos candidato
WHERE compra.id = candidato.compra_id;

CREATE TABLE solicitacao_presenca_compra (
    id UUID PRIMARY KEY,
    compra_id UUID NOT NULL,
    solicitante_id UUID NOT NULL,
    ciclo_operacional BIGINT NOT NULL,
    solicitada_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL,
    encerrada_por_id UUID,
    encerrada_em TIMESTAMPTZ,
    motivo_cancelamento VARCHAR(30),
    CONSTRAINT fk_solicitacao_presenca_compra
        FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_solicitacao_presenca_solicitante_mesma_compra
        FOREIGN KEY (compra_id, solicitante_id)
        REFERENCES participante_compra (compra_id, id),
    CONSTRAINT fk_solicitacao_presenca_encerrada_por_mesma_compra
        FOREIGN KEY (compra_id, encerrada_por_id)
        REFERENCES participante_compra (compra_id, id),
    CONSTRAINT ck_solicitacao_presenca_estado
        CHECK (estado IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
    CONSTRAINT ck_solicitacao_presenca_cancelamento
        CHECK (motivo_cancelamento IS NULL OR motivo_cancelamento IN
            ('SOLICITANTE', 'SEM_PRESENTES', 'COMPRA_FINALIZADA')),
    CONSTRAINT ck_solicitacao_presenca_encerramento
        CHECK (
            (estado = 'PENDENTE' AND encerrada_por_id IS NULL AND encerrada_em IS NULL AND motivo_cancelamento IS NULL)
            OR (estado IN ('APROVADA', 'REJEITADA') AND encerrada_por_id IS NOT NULL
                AND encerrada_em IS NOT NULL AND motivo_cancelamento IS NULL)
            OR (estado = 'CANCELADA' AND encerrada_em IS NOT NULL AND motivo_cancelamento IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uk_solicitacao_presenca_pendente_por_participante
    ON solicitacao_presenca_compra (compra_id, solicitante_id)
    WHERE estado = 'PENDENTE';

CREATE INDEX ix_solicitacao_presenca_pendente_por_compra
    ON solicitacao_presenca_compra (compra_id, solicitada_em, id)
    WHERE estado = 'PENDENTE';
