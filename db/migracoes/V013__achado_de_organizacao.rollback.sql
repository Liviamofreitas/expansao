-- Rollback da V013. A sinalização do cap. 8.1 volta a não existir: a cópia de
-- conflito continua não sendo publicada, mas também não aparece em lugar nenhum.
BEGIN;
DROP TABLE IF EXISTS achado_de_organizacao;
COMMIT;
