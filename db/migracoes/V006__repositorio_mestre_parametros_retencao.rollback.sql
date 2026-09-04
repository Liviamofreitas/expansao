BEGIN;
ALTER TABLE book
    DROP CONSTRAINT IF EXISTS book_retencao_coerente,
    DROP COLUMN IF EXISTS retencao_ate,
    DROP COLUMN IF EXISTS retencao_modo;
DROP TABLE IF EXISTS parametro;
ALTER TABLE tipo_documental
    DROP CONSTRAINT IF EXISTS tipo_repos_alternativos_lista,
    DROP COLUMN IF EXISTS repositorios_alternativos,
    DROP COLUMN IF EXISTS repositorio_mestre;
COMMIT;
