-- =============================================================================
-- SGDF — F3-06 · acesso observado (migração V017, requisito SEC-10)
--
-- A rede que garante que a recertificação não invente deriva nem perca
-- concessão: sem a unicidade, o mesmo acesso repetido no dia viraria N linhas e
-- o relatório apontaria mudança onde não houve.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
DECLARE
    v_c1 uuid := gen_random_uuid();
    v_c2 uuid := gen_random_uuid();
BEGIN
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('auditora.silva', DATE '2027-02-10', ARRAY['AUDITORIA'], ARRAY[]::uuid[]);
    PERFORM teste_ok('SEC-10 · o acesso do auditor é registrado — ele nunca escreve, '
                     'e a trilha sozinha o omitiria');

    -- 1. O mesmo acesso no mesmo dia é uma linha só ------------------------------
    BEGIN
        INSERT INTO acesso_observado (ator, dia, papeis, contratos)
        VALUES ('auditora.silva', DATE '2027-02-10', ARRAY['AUDITORIA'], ARRAY[]::uuid[]);
        PERFORM teste_falhou('SEC-10 · o mesmo acesso do dia foi gravado duas vezes');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('SEC-10 · o mesmo acesso no mesmo dia é uma linha só — N linhas '
                         'iguais virariam deriva de concessão onde não houve');
    END;

    -- 2. Concessão diferente no mesmo dia é linha nova ---------------------------
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('auditora.silva', DATE '2027-02-10',
            ARRAY['AUDITORIA', 'APROVADOR_DAF'], ARRAY[]::uuid[]);
    PERFORM teste_ok('SEC-10 · ganhar um papel no meio do dia produz a segunda linha — '
                     'a deriva aparece na série em vez de ser sobrescrita');

    -- 3. O recorte por contrato faz parte da chave -------------------------------
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('recortado', DATE '2027-02-10', ARRAY['PUBLICADOR_AP'], ARRAY[v_c1]);
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('recortado', DATE '2027-02-10', ARRAY['PUBLICADOR_AP'], ARRAY[v_c1, v_c2]);
    PERFORM teste_ok('SEC-10 · ganhar um contrato no escopo também é concessão nova — '
                     'SEC-10 pede a recertificação POR CONTRATO');

    -- 4. Observação sem papel não é observação -----------------------------------
    BEGIN
        INSERT INTO acesso_observado (ator, dia, papeis, contratos)
        VALUES ('sem.papel', DATE '2027-02-10', ARRAY[]::text[], ARRAY[]::uuid[]);
        PERFORM teste_falhou('SEC-10 · acesso sem papel nenhum foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('SEC-10 · acesso sem papel é recusado — o Autorizador já barra '
                         'na fronteira, e a linha não diria nada');
    END;

    BEGIN
        INSERT INTO acesso_observado (ator, dia, papeis, contratos)
        VALUES ('   ', DATE '2027-02-10', ARRAY['AUDITORIA'], ARRAY[]::uuid[]);
        PERFORM teste_falhou('SEC-10 · ator em branco foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('SEC-10 · ator em branco é recusado — uma recertificação '
                         'precisa dizer de quem é o acesso');
    END;

    -- 5. O que a unicidade NÃO garante, dito explicitamente -----------------------
    --
    -- A ordem do array é do chamador. {A,B} e {B,A} passariam como duas
    -- concessões distintas se o Java não ordenasse — o banco não tem como saber
    -- que são a mesma. A garantia vive no RegistroDeAcesso, e o teste dela está
    -- em TestesDeRecertificacao.aOrdemDoArrayNaoInventaDeriva.
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('desordenado', DATE '2027-02-10', ARRAY['A', 'B'], ARRAY[]::uuid[]);
    INSERT INTO acesso_observado (ator, dia, papeis, contratos)
    VALUES ('desordenado', DATE '2027-02-10', ARRAY['B', 'A'], ARRAY[]::uuid[]);
    PERFORM teste_ok('SEC-10 · o banco NÃO normaliza a ordem do array: {A,B} e {B,A} '
                     'passam como duas. A ordenação é do RegistroDeAcesso');
END $$;

ROLLBACK;
