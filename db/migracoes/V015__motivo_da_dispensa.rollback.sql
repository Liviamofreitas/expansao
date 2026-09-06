-- Rollback da V015. ATENÇÃO: dispensa por exceção e por condicional voltam a ser
-- indistinguíveis, e o indicador de exceções aprovadas volta a contar as duas.
BEGIN;
DROP INDEX IF EXISTS ix_exigencia_dispensa;
ALTER TABLE exigencia DROP CONSTRAINT IF EXISTS exigencia_dispensa_com_motivo;
ALTER TABLE exigencia DROP COLUMN IF EXISTS dispensa_motivo;
COMMIT;
