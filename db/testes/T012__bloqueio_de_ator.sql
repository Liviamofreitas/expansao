-- =============================================================================
-- SGDF — SEC-06 · bloqueio de ator (migração V019)
--
-- A rede que impede um bloqueio sem fim. Um bloqueio permanente vira chamado de
-- suporte, e chamado repetido vira um bypass que alguém cria e ninguém remove.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
BEGIN
    INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
    VALUES ('sondador', now() + INTERVAL '15 minutes', 10, '10 negativas em 15 minutos');
    PERFORM teste_ok('SEC-06 · o bloqueio com expiração futura é aceito');

    -- 1. Sem fim ----------------------------------------------------------------
    BEGIN
        INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
        VALUES ('eterno', now() - INTERVAL '1 day', 10, 'expira antes de começar');
        PERFORM teste_falhou('SEC-06 · bloqueio que expira antes de começar foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('SEC-06 · bloqueio que expira antes de começar é recusado — '
                         'não é um bloqueio vencido, é um estado impossível');
    END;

    -- 2. Um ativo por ator ------------------------------------------------------
    BEGIN
        INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
        VALUES ('sondador', now() + INTERVAL '30 minutes', 20, 'segundo bloqueio');
        PERFORM teste_falhou('SEC-06 · dois bloqueios ativos para o mesmo ator');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('SEC-06 · um bloqueio ativo por ator — dois seriam duas '
                         'expirações para o mesmo fato, e "até quando?" perderia resposta');
    END;

    -- 3. Liberado abre espaço para um novo ---------------------------------------
    UPDATE bloqueio_de_ator SET liberado_em = now(), liberado_por = 'admin.tec'
    WHERE ator = 'sondador';
    INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
    VALUES ('sondador', now() + INTERVAL '15 minutes', 12, 'voltou a sondar');
    PERFORM teste_ok('SEC-06 · liberado o anterior, um novo bloqueio é aceito — quem '
                     'foi liberado por engano e volta a sondar é bloqueado de novo');

    -- 4. Liberação pela metade ---------------------------------------------------
    BEGIN
        UPDATE bloqueio_de_ator SET liberado_em = now()
        WHERE ator = 'sondador' AND liberado_em IS NULL;
        PERFORM teste_falhou('SEC-06 · liberação sem dizer quem liberou foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('SEC-06 · liberar sem dizer quem é recusado — a liberação é '
                         'ato humano e alguém vai perguntar quem autorizou');
    END;

    -- 5. Bloqueio sem negativa ---------------------------------------------------
    BEGIN
        INSERT INTO bloqueio_de_ator (ator, expira_em, negativas, motivo)
        VALUES ('sem.motivo', now() + INTERVAL '15 minutes', 0, 'nenhuma negativa');
        PERFORM teste_falhou('SEC-06 · bloqueio com zero negativas foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('SEC-06 · bloqueio exige ao menos uma negativa — bloquear sem '
                         'nada contado é bloquear sem causa');
    END;
END $$;

ROLLBACK;
