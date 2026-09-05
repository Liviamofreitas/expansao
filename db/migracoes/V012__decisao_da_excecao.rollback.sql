-- Rollback da V012. ATENÇÃO: as negativas registradas passam a ser
-- indistinguíveis de solicitações pendentes.
BEGIN;
DROP INDEX IF EXISTS ix_excecao_pendente;
DROP INDEX IF EXISTS ux_excecao_aberta;
ALTER TABLE excecao DROP CONSTRAINT IF EXISTS excecao_negativa_com_motivo;
ALTER TABLE excecao DROP CONSTRAINT IF EXISTS excecao_decisao_completa;
ALTER TABLE excecao DROP COLUMN IF EXISTS motivo_decisao;
ALTER TABLE excecao DROP COLUMN IF EXISTS situacao;
ALTER TABLE excecao
    ADD CONSTRAINT excecao_aprovacao_completa CHECK (
        (aprovador IS NULL AND aprovado_em IS NULL) OR
        (aprovador IS NOT NULL AND aprovado_em IS NOT NULL));
COMMIT;
