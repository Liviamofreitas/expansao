-- Rollback da V018. ATENÇÃO: volta a ser impossível distinguir "o job rodou e
-- não achou nada" de "o job não rodou" — e o segundo deixa o painel verde.
BEGIN;
DROP TABLE IF EXISTS execucao_de_job;
COMMIT;
