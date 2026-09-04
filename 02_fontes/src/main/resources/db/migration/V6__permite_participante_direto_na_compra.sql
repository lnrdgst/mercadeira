ALTER TABLE participante_compra
    ALTER COLUMN participante_lista_origem_id DROP NOT NULL;

ALTER TABLE participante_compra
    DROP CONSTRAINT uk_participante_compra_origem;

CREATE UNIQUE INDEX ux_participante_compra_origem_quando_presente
    ON participante_compra (participante_lista_origem_id)
    WHERE participante_lista_origem_id IS NOT NULL;
