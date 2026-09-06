-- Rollback da V016. ATENÇÃO: o banco volta a aceitar FATURADO sem ateste_em, e
-- um ciclo assim desaparece dos dois lados da fração do indicador D+3.
BEGIN;
DROP INDEX IF EXISTS ix_ciclo_d3;
ALTER TABLE ciclo DROP CONSTRAINT IF EXISTS ciclo_nf_nao_antecede_ateste;
ALTER TABLE ciclo DROP CONSTRAINT IF EXISTS ciclo_nf_antes_de_faturado;
ALTER TABLE ciclo DROP CONSTRAINT IF EXISTS ciclo_ateste_antes_de_atestado;
COMMIT;
