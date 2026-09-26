CREATE INDEX IF NOT EXISTS idx_compra_status_finalizada_em
    ON compra (status, finalizada_em DESC);
