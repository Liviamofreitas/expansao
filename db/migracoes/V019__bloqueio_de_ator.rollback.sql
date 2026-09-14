-- Rollback da V019. ATENÇÃO: a tentativa repetida de acesso volta a ser apenas
-- registrada na trilha, sem reação nenhuma.
BEGIN;
DROP TABLE IF EXISTS bloqueio_de_ator;
COMMIT;
