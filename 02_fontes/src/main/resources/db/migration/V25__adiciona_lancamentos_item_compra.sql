CREATE TABLE lancamento_item_compra (
    id UUID PRIMARY KEY,
    item_compra_id UUID NOT NULL REFERENCES item_compra(id),
    preco_unitario NUMERIC(19, 4) NOT NULL,
    quantidade NUMERIC(12, 3) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lancamento_item_compra_preco_unitario_positivo CHECK (preco_unitario > 0),
    CONSTRAINT ck_lancamento_item_compra_quantidade_positiva CHECK (quantidade > 0)
);

CREATE INDEX idx_lancamento_item_compra_item_criado_em
    ON lancamento_item_compra (item_compra_id, criado_em, id);

INSERT INTO lancamento_item_compra (
    id, item_compra_id, preco_unitario, quantidade, criado_em, atualizado_em
)
SELECT (
        substr(md5('lancamento-item-compra:' || ic.id::text), 1, 8) || '-' ||
        substr(md5('lancamento-item-compra:' || ic.id::text), 9, 4) || '-' ||
        substr(md5('lancamento-item-compra:' || ic.id::text), 13, 4) || '-' ||
        substr(md5('lancamento-item-compra:' || ic.id::text), 17, 4) || '-' ||
        substr(md5('lancamento-item-compra:' || ic.id::text), 21, 12)
    )::uuid,
    ic.id,
    ic.preco_unitario,
    ic.quantidade_comprada,
    COALESCE(ic.marcado_em, c.iniciada_em),
    COALESCE(ic.marcado_em, c.iniciada_em)
FROM item_compra ic
JOIN compra c ON c.id = ic.compra_id
WHERE ic.preco_unitario > 0
  AND ic.quantidade_comprada > 0;
