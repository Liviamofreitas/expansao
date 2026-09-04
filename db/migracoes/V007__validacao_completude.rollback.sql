-- Rollback de V007.
BEGIN;

DROP VIEW  IF EXISTS validacao_vigente;
DROP TABLE IF EXISTS validacao_documento;

ALTER TABLE regra_reconhecimento
    DROP CONSTRAINT IF EXISTS regra_recon_essenciais_validos;

DROP FUNCTION IF EXISTS regra_recon_essenciais_validos(jsonb, jsonb);

ALTER TABLE regra_reconhecimento
    DROP COLUMN IF EXISTS campos_essenciais;

COMMIT;
