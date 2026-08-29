ALTER TABLE familia
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    ADD COLUMN codigo_ingresso VARCHAR(32);

UPDATE familia
SET codigo_ingresso = UPPER(REPLACE(id::TEXT, '-', ''))
WHERE codigo_ingresso IS NULL;

ALTER TABLE familia
    ALTER COLUMN codigo_ingresso SET NOT NULL,
    ADD CONSTRAINT ck_familia_status
        CHECK (status IN ('ATIVA', 'INATIVA')),
    ADD CONSTRAINT uk_familia_codigo_ingresso
        UNIQUE (codigo_ingresso);

ALTER TABLE lista_compra
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'EM_PREPARACAO',
    ADD CONSTRAINT ck_lista_compra_status
        CHECK (status IN ('EM_PREPARACAO', 'EM_COMPRA', 'FINALIZADA', 'CANCELADA'));

ALTER TABLE item_lista
    ADD COLUMN removido_em TIMESTAMPTZ,
    ADD COLUMN ordem_exibicao INTEGER;

WITH itens_ordenados AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY lista_compra_id
            ORDER BY criado_em, id
        )::INTEGER AS ordem_exibicao
    FROM item_lista
)
UPDATE item_lista
SET ordem_exibicao = itens_ordenados.ordem_exibicao
FROM itens_ordenados
WHERE item_lista.id = itens_ordenados.id;

ALTER TABLE item_lista
    ALTER COLUMN ordem_exibicao SET NOT NULL;

CREATE INDEX ix_item_lista_ativos_ordem_exibicao
    ON item_lista (lista_compra_id, ordem_exibicao)
    WHERE removido_em IS NULL;

ALTER TABLE item_compra
    ADD COLUMN ordem_exibicao INTEGER;

WITH itens_ordenados AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY compra_id
            ORDER BY id
        )::INTEGER AS ordem_exibicao
    FROM item_compra
)
UPDATE item_compra
SET ordem_exibicao = itens_ordenados.ordem_exibicao
FROM itens_ordenados
WHERE item_compra.id = itens_ordenados.id;

ALTER TABLE item_compra
    ALTER COLUMN ordem_exibicao SET NOT NULL;

CREATE INDEX ix_item_compra_ordem_exibicao
    ON item_compra (compra_id, ordem_exibicao);

ALTER TABLE compra
    ADD COLUMN reaberta_em TIMESTAMPTZ,
    ADD COLUMN reaberta_por_membro_familia_id UUID,
    ADD CONSTRAINT fk_compra_reaberta_por
        FOREIGN KEY (reaberta_por_membro_familia_id) REFERENCES membro_familia (id),
    ADD CONSTRAINT ck_compra_reabertura_preenchida
        CHECK (
            (reaberta_em IS NULL AND reaberta_por_membro_familia_id IS NULL)
            OR (reaberta_em IS NOT NULL AND reaberta_por_membro_familia_id IS NOT NULL)
        );

CREATE UNIQUE INDEX uk_solicitacao_entrada_familia_pendente
    ON solicitacao_entrada_familia (familia_id, solicitante_usuario_id)
    WHERE status = 'PENDENTE';
