ALTER TABLE registro_financeiro_compra
    ADD COLUMN chave_nfce VARCHAR(44),
    ADD COLUMN url_consulta VARCHAR(1000),
    ADD COLUMN cnpj_emitente VARCHAR(14),
    ADD COLUMN data_hora_documento TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_registro_financeiro_compra_chave_nfce
    ON registro_financeiro_compra (compra_id, chave_nfce)
    WHERE chave_nfce IS NOT NULL;
