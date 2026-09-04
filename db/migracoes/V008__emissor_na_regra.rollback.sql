-- Rollback de V008.
BEGIN;

DROP INDEX IF EXISTS ux_regra_recon_versao;

ALTER TABLE regra_reconhecimento
    ADD CONSTRAINT regra_recon_versao_unica UNIQUE (tipo_id, versao);

ALTER TABLE regra_reconhecimento
    DROP COLUMN IF EXISTS emissor;

COMMIT;
