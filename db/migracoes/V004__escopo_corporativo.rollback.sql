-- Reversão da V004. Destrutiva para exigências corporativas: elas não têm
-- ciclo_id e violariam o NOT NULL restaurado. Só em DEV.
BEGIN;
DROP VIEW IF EXISTS exigencia_do_ciclo;
DROP INDEX IF EXISTS ix_exigencia_corporativa_competencia;
DROP INDEX IF EXISTS ux_exigencia_corporativa;
DROP INDEX IF EXISTS ux_exigencia_de_ciclo;
DELETE FROM exigencia WHERE ciclo_id IS NULL;
ALTER TABLE exigencia
    DROP CONSTRAINT IF EXISTS exigencia_corporativa_sem_profissional,
    DROP CONSTRAINT IF EXISTS exigencia_competencia_formato,
    DROP CONSTRAINT IF EXISTS exigencia_endereco_exclusivo,
    DROP COLUMN IF EXISTS competencia,
    DROP COLUMN IF EXISTS empresa_id;
ALTER TABLE exigencia ALTER COLUMN ciclo_id SET NOT NULL;
ALTER TABLE exigencia ADD CONSTRAINT exigencia_unica
    UNIQUE NULLS NOT DISTINCT (ciclo_id, tipo_id, evento, profissional_id);
ALTER TABLE contrato_servico
    DROP CONSTRAINT IF EXISTS contrato_ativo_exige_empresa,
    DROP COLUMN IF EXISTS empresa_id;
DROP TABLE IF EXISTS empresa;
COMMIT;
