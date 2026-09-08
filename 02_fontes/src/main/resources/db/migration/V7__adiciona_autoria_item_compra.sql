ALTER TABLE item_compra
    ADD COLUMN adicionado_por_participante_compra_id UUID,
    ADD COLUMN adicionado_em TIMESTAMPTZ,
    ADD CONSTRAINT fk_item_compra_adicionado_por_participante_compra
        FOREIGN KEY (adicionado_por_participante_compra_id) REFERENCES participante_compra (id),
    ADD CONSTRAINT ck_item_compra_autoria_adicao
        CHECK (
            (adicionado_durante_compra
                AND adicionado_por_participante_compra_id IS NOT NULL
                AND adicionado_em IS NOT NULL)
            OR (NOT adicionado_durante_compra
                AND adicionado_por_participante_compra_id IS NULL
                AND adicionado_em IS NULL)
        );
