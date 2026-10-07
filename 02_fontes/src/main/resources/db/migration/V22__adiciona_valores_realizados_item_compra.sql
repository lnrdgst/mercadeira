ALTER TABLE item_compra
    ADD COLUMN preco_unitario NUMERIC(19, 4),
    ADD COLUMN quantidade_comprada NUMERIC(12, 3),
    ADD CONSTRAINT ck_item_compra_dados_financeiros_completos CHECK (
        (preco_unitario IS NULL OR (preco_unitario > 0 AND quantidade_comprada IS NOT NULL))
        AND (quantidade_comprada IS NULL OR quantidade_comprada > 0)
    );
