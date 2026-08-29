ALTER TABLE usuario
    ADD COLUMN senha_hash VARCHAR(255);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM usuario) THEN
        RAISE EXCEPTION 'Nao foi possivel aplicar V3: existem usuarios sem senha_hash.'
            USING HINT = 'Defina uma estrategia explicita de credenciais para os usuarios existentes antes de aplicar esta migration.';
    END IF;
END $$;

ALTER TABLE usuario
    ALTER COLUMN senha_hash SET NOT NULL;
