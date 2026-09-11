ALTER TABLE item_compra
    ADD COLUMN restaurado_por_participante_compra_id UUID,
    ADD COLUMN restaurado_em TIMESTAMPTZ,
    ADD CONSTRAINT fk_item_compra_restaurado_por_mesma_compra
        FOREIGN KEY (compra_id, restaurado_por_participante_compra_id)
        REFERENCES participante_compra (compra_id, id),
    ADD CONSTRAINT ck_item_compra_restauracao_preenchida
        CHECK (
            (restaurado_por_participante_compra_id IS NULL)
            = (restaurado_em IS NULL)
        ),
    ADD CONSTRAINT ck_item_compra_restauracao_marcacao
        CHECK (
            restaurado_em IS NULL
            OR (
                status <> 'PENDENTE'
                AND marcado_por_membro_familia_id IS NOT NULL
                AND marcado_em IS NOT NULL
                AND marcado_em = restaurado_em
            )
        ),
    DROP CONSTRAINT ck_item_compra_decisao_status,
    ADD CONSTRAINT ck_item_compra_decisao_status
        CHECK (
            decisao_remocao IS NULL
            OR (
                decisao_remocao = 'APROVADA'
                AND (
                    status = 'REMOVIDO'
                    OR (
                        status = 'NO_CARRINHO'
                        AND restaurado_por_participante_compra_id IS NOT NULL
                        AND restaurado_em IS NOT NULL
                        AND remocao_resolvida_em IS NOT NULL
                        AND restaurado_em >= remocao_resolvida_em
                    )
                )
            )
            OR (decisao_remocao = 'REJEITADA' AND status = 'NO_CARRINHO')
        );