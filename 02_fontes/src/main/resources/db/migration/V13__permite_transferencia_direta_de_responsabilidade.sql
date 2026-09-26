ALTER TABLE compra
    DROP CONSTRAINT ck_compra_responsabilidade_motivo,
    ADD CONSTRAINT ck_compra_responsabilidade_motivo
        CHECK (responsabilidade_motivo IS NULL OR responsabilidade_motivo IN
            ('INICIO_COMPRA', 'PRIMEIRA_ENTRADA', 'SUCESSAO', 'REASSUNCAO',
             'TRANSFERENCIA_DIRETA', 'SEM_PRESENTES', 'BOOTSTRAP_V10', 'LEGADO_SEM_ELEGIVEL'));
