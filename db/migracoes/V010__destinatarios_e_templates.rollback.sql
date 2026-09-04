-- Rollback da V010. ATENÇÃO: a unicidade diária volta a incluir `tipo`, e com
-- ela volta a possibilidade de três e-mails no mesmo dia para a mesma pessoa.
BEGIN;
DELETE FROM parametro WHERE chave = 'notificacao.modo_sombra' AND escopo = 'GLOBAL';
DROP TABLE IF EXISTS template_notificacao;
DROP TABLE IF EXISTS destinatario;
ALTER TABLE notificacao DROP CONSTRAINT IF EXISTS notificacao_diaria_unica;
ALTER TABLE notificacao
    ADD CONSTRAINT notificacao_diaria_unica
    UNIQUE (ciclo_id, tipo, destinatario, data_referencia);
COMMIT;
