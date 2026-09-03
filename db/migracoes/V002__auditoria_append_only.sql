-- =============================================================================
-- SGDF — V002 · Trilha de auditoria append-only
-- Referência: cap. 5.2 (log_auditoria), cap. 15.2 (SEC-03), história F0-08.
--
-- Critério de aceite da F0-08: "UPDATE em log_auditoria negado pelo banco".
-- Verificação do SEC-03: "tentativa de alteração falha em teste".
--
-- A garantia é dupla e deliberada:
--   1. GRANT/REVOKE — a role da aplicação não recebe UPDATE nem DELETE;
--   2. RULE — bloqueia mesmo que alguém conceda a permissão por engano depois,
--      inclusive para o dono da tabela.
-- Só (1) seria insuficiente: um GRANT posterior reabriria o buraco em silêncio.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Permissões: a aplicação insere e lê a trilha; nunca altera nem remove.
-- -----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO sgdf_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sgdf_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sgdf_app;

REVOKE UPDATE, DELETE, TRUNCATE ON log_auditoria FROM sgdf_app;
REVOKE UPDATE, DELETE, TRUNCATE ON log_auditoria FROM PUBLIC;

-- book é append-only pelo cap. 10 (republicação cria v{N+1}, nunca altera).
REVOKE UPDATE, DELETE, TRUNCATE ON book FROM sgdf_app;
REVOKE UPDATE, DELETE, TRUNCATE ON book FROM PUBLIC;

-- versao_matriz é append-only pelo cap. 5.1.
REVOKE UPDATE, DELETE, TRUNCATE ON versao_matriz FROM sgdf_app;
REVOKE UPDATE, DELETE, TRUNCATE ON versao_matriz FROM PUBLIC;

-- Novas tabelas criadas por migrations futuras herdam o GRANT de leitura/escrita,
-- mas nunca herdam permissão sobre as três acima.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sgdf_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sgdf_app;

-- -----------------------------------------------------------------------------
-- 2. Rules: a negação vale para qualquer papel, inclusive o dono e o superusuário
--    em operação normal. É o que sustenta o "sem UPDATE/DELETE (revogado no
--    banco)" do cap. 5.2 como propriedade da tabela, não da configuração.
-- -----------------------------------------------------------------------------
CREATE RULE log_auditoria_sem_update AS
    ON UPDATE TO log_auditoria DO INSTEAD NOTHING;

CREATE RULE log_auditoria_sem_delete AS
    ON DELETE TO log_auditoria DO INSTEAD NOTHING;

COMMENT ON TABLE log_auditoria IS
    'Append-only (SEC-03). UPDATE e DELETE são no-op por RULE e negados por '
    'permissão. O expurgo por temporalidade (LGPD-02, pendência A08) será uma '
    'migration própria, que remove a rule, expurga sob transação registrada e a '
    'recria — nunca uma operação de rotina da aplicação.';

COMMIT;
