-- V1-V7 nao registram autoria de finalizacao. Nao e seguro deduzi-la do iniciador.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM compra WHERE status = 'FINALIZADA' OR finalizada_em IS NOT NULL) THEN
        RAISE EXCEPTION 'V8: existem compras com finalizacao anterior sem autoria registrada.'
            USING HINT = 'Defina um tratamento explicito para os dados legados antes de aplicar V8. Nao deduza o autor a partir do iniciador.';
    END IF;
END $$;

ALTER TABLE participante_compra
    ADD CONSTRAINT uk_participante_compra_compra_id UNIQUE (compra_id, id);

ALTER TABLE compra
    ADD COLUMN finalizada_por_participante_compra_id UUID,
    ADD CONSTRAINT fk_compra_finalizada_por_mesma_compra
        FOREIGN KEY (id, finalizada_por_participante_compra_id)
        REFERENCES participante_compra (compra_id, id),
    ADD CONSTRAINT ck_compra_finalizacao_preenchida
        CHECK (
            (finalizada_em IS NULL
                AND finalizada_por_participante_compra_id IS NULL
                AND status <> 'FINALIZADA')
            OR (finalizada_em IS NOT NULL
                AND finalizada_por_participante_compra_id IS NOT NULL)
        );
