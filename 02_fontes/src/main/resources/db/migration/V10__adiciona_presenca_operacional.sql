ALTER TABLE participante_compra
    ADD COLUMN presenca_operacional VARCHAR(20) NOT NULL DEFAULT 'NAO_INFORMADA',
    ADD COLUMN presenca_alterada_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_participante_compra_presenca_estado
        CHECK (presenca_operacional IN ('NAO_INFORMADA', 'PRESENTE', 'NAO_PRESENTE')),
    ADD CONSTRAINT ck_participante_compra_presenca_timestamp
        CHECK (
            (presenca_operacional = 'NAO_INFORMADA' AND presenca_alterada_em IS NULL)
            OR (presenca_operacional IN ('PRESENTE', 'NAO_PRESENTE') AND presenca_alterada_em IS NOT NULL)
        );
