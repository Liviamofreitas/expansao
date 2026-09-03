-- Reversão da V002. Cap. 17: migrations versionadas e reversíveis.
-- Reverter esta migration REABRE a trilha de auditoria para alteração; só deve
-- ser executado com registro formal de mudança (cap. 20).
BEGIN;
DROP RULE IF EXISTS log_auditoria_sem_update ON log_auditoria;
DROP RULE IF EXISTS log_auditoria_sem_delete ON log_auditoria;
GRANT UPDATE, DELETE ON log_auditoria, book, versao_matriz TO sgdf_app;
COMMIT;
