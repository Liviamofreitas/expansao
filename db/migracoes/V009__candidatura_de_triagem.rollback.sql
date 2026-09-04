-- Rollback da V009. A fila volta a ser lida de `documento` sozinho — e volta o
-- RA-01, porque o documento não tem contrato.
BEGIN;
DROP VIEW IF EXISTS fila_de_triagem;
DROP TABLE IF EXISTS candidatura;
COMMIT;
